package ch.ge.rabbitmq.load;

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
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Methodes et variables communues au producteur et au consommateur.
 */
public class Utils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    private static final String UAA_TOKEN_URL = "***REMOVED***/oauth/token";
    private static final String UAA_CLIENT_ID = "***REMOVED***";
    private static final String UAA_CLIENT_SECRET = "***REMOVED***_secret";
    private static final String UAA_GRANT_TYPE = "password";
    private static final String UAA_RESPONSE_TYPE = "token";
    private static final String GINA_USERNAME = "***REMOVED***";
    private static final String GINA_PASSWORD = System.getenv("password");  // mot de passe AD

    private static final String RABBITMQ_URL = "***REMOVED***";
    private static final int RABBITMQ_PORT = 5672;
    private static final String RABBITMQ_VIRTUAL_HOST = "aelenu";
    public static final String RABBITMQ_EXCHANGE = "simetier1-to-enu-main";
    public static final String RABBITMQ_QUEUE_NAME = "simetier1-to-enu-main-q";
    public static final String RABBITMQ_ROUTING_KEY = RABBITMQ_QUEUE_NAME;

    private static final String LOCK = "lock";

    private Utils() {
    }

    public static String getAccessToken(SSLContext sslContext, List<NameValuePair> urlParameters) throws IOException {
        HttpPost post = new HttpPost(UAA_TOKEN_URL);
        post.setEntity(new UrlEncodedFormEntity(urlParameters));
        SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslContext, new NoopHostnameVerifier());
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(factory).build();
        CloseableHttpResponse response = httpClient.execute(post);
        JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
        return obj.getString("access_token");
    }

    public static SSLContext getSSLContext() throws NoSuchAlgorithmException, KeyManagementException{
        TrustManager[] trustAllCerts = Utils.getTrustManager();
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        return sslContext;
    }

    public static ConnectionFactory getConnectionFactory(SSLContext sslContext, String accessToken) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(RABBITMQ_URL);
        connectionFactory.setVirtualHost(RABBITMQ_VIRTUAL_HOST);
        connectionFactory.setPassword(accessToken);
        connectionFactory.setPort(RABBITMQ_PORT);
        connectionFactory.useSslProtocol(sslContext);
        return connectionFactory;
    }

    public static List<NameValuePair> getUrlParameters() {
        if (GINA_PASSWORD == null) {
            throw new IllegalStateException("Le mot de passe AD n'a pas ete fourni");
        }
        List<NameValuePair> urlParameters = new ArrayList<>();
        urlParameters.add(new BasicNameValuePair("client_id", UAA_CLIENT_ID));
        urlParameters.add(new BasicNameValuePair("client_secret", UAA_CLIENT_SECRET));
        urlParameters.add(new BasicNameValuePair("grant_type", UAA_GRANT_TYPE));
        urlParameters.add(new BasicNameValuePair("username", GINA_USERNAME));
        urlParameters.add(new BasicNameValuePair("password", GINA_PASSWORD));
        urlParameters.add(new BasicNameValuePair("response_type", UAA_RESPONSE_TYPE));
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

    public static void wait(long seconds, String info) {
        try {
            LOGGER.info("Attente commence ({})", info);
            TimeUnit.SECONDS.sleep(seconds);
            LOGGER.info("Attente finie ({})", info);
        } catch (InterruptedException e) {
            LOGGER.warn("Recu exception", e);
        }
    }

}
