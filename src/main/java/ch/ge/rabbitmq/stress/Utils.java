/*
 * rabbitmq-stress-test
 *
 * Copyright (C) 2021 République et canton de Genève
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.ge.rabbitmq.stress;

import com.rabbitmq.client.ConnectionFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 * Functions and variables that are common to the producer and the consumer.
 */
public class Utils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private static final Properties PROPERTIES = new Properties();

    // not very clean, yet handy
    static int NB_SENT_MESSAGES = 0;

    /** filling of the message body, to attain 100 kB */
    private static final char[] MESSAGE_FILLING;

    static {
        final int MESSAGE_SIZE = 100 * 1000;
        char[] array = new char[MESSAGE_SIZE];
        Arrays.fill(array, '*');
        MESSAGE_FILLING = array;
    }

    private Utils() {
    }

    static String getAccessToken(SSLContext sslContext, List<NameValuePair> urlParameters) throws IOException {
        HttpPost post = new HttpPost(getProperty("uaa.token-url"));
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(factory).build();
        CloseableHttpResponse response = httpClient.execute(post);
        JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
        return obj.getString("access_token");
    }

    static SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException{
        TrustManager[] trustAllCerts = Utils.getTrustManager();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        return sslContext;
    }

    static ConnectionFactory getConnectionFactory(SSLContext sslContext, String accessToken) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(getProperty("rabbitmq.url"));
        connectionFactory.setVirtualHost(getProperty("rabbitmq.virtual-host"));
        connectionFactory.setPassword(accessToken);
        connectionFactory.setPort(getIntegerProperty("rabbitmq.port"));
        connectionFactory.useSslProtocol(sslContext);
        return connectionFactory;
    }

    static List<NameValuePair> getUrlParameters() {
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("client_id", getProperty("uaa.client-id")));
        urlParameters.add(new BasicNameValuePair("client_secret", getProperty("uaa.client-secret")));
        urlParameters.add(new BasicNameValuePair("grant_type", getProperty("uaa.grant-type")));
        urlParameters.add(new BasicNameValuePair("username", getProperty("ad.username")));
        urlParameters.add(new BasicNameValuePair("password", getProperty("ad.password")));
        urlParameters.add(new BasicNameValuePair("response_type", getProperty("uaa.response-type")));
        return urlParameters;
    }

    private static TrustManager[] getTrustManager() {
        return new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }

                public void checkClientTrusted(
                    X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                    X509Certificate[] certs, String authType) {
                }
            }
        };
    }

    static void wait(int milliseconds, String info) {
        try {
            LOGGER.info("Attente commence ({})", info);
            TimeUnit.MILLISECONDS.sleep(milliseconds);
            LOGGER.info("Attente finie ({})", info);
        } catch (InterruptedException e) {
            LOGGER.warn("Recu exception", e);
        }
    }

    static void readProperties(String[] args) {
        if (args.length != 2) {
            throw new IllegalStateException("2 arguments are expected: password file + other properties file");
        }

        String passwordFile = args[0];
        String propsFile = args[1];

        loadProperties(passwordFile, PROPERTIES);
        loadProperties(propsFile, PROPERTIES);
        LOGGER.info("Read {} proprietes", PROPERTIES.size());
    }

    private static void loadProperties(String fileName, Properties props) {
        LOGGER.info("Loading file [{}]", fileName);
        try (FileInputStream is = new FileInputStream(fileName)) {
            props.load(is);
        } catch(Exception e) {
            throw new IllegalStateException("Error while reading file [" + fileName + "]", e);
        }
    }

    static int getIntegerProperty(String name) {
        return Integer.parseInt(getProperty(name));
    }

    static String getProperty(String name) {
        String val = PROPERTIES.getProperty(name);
        if (val == null) {
            throw new IllegalStateException("Property [" + name + "] was not found");
        }
        return val;
    }

    static int getScenario() {
        return getIntegerProperty("scenario.type");
    }

    static int getNbIterations() {
        return getIntegerProperty("scenario" + getScenario() + ".iterations");
    }

    static int getNbMessages(int iteration) {
        if (getScenario() == 2) {
            return 1 + getIntegerProperty("scenario2.increment") * (iteration - 1);
        } else {
            return 1;
        }
    }

    static int getInterval() {
        return getIntegerProperty("scenario" + getScenario() + ".interval");
    }

    static String createNextMessage() {
        NB_SENT_MESSAGES++;
        return new StringBuilder("Message ")
                .append(NB_SENT_MESSAGES)
                .append(" ")
                .append(MESSAGE_FILLING)
                .toString();
    }

}
