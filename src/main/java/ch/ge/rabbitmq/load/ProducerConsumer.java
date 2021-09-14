package ch.ge.rabbitmq.load;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.CancelCallback;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.apache.http.NameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static ch.ge.rabbitmq.load.Utils.NB_SENT_MESSAGES;
import static ch.ge.rabbitmq.load.Utils.createNextMessage;
import static ch.ge.rabbitmq.load.Utils.getInterval;
import static ch.ge.rabbitmq.load.Utils.getNbIterations;
import static ch.ge.rabbitmq.load.Utils.getNbMessages;
import static ch.ge.rabbitmq.load.Utils.getProperty;
import static ch.ge.rabbitmq.load.Utils.getScenario;

/**
 * Envoi et reception d'un lot de messages de RabbitMQ.
 */
public class ProducerConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerConsumer.class);

    /**
     * Lancement en parallele de la production et de la consommation.
     */
    public static void main(String[] args) {
        Utils.readProperties(args);

        // production
        Executors.newCachedThreadPool().submit(() -> {
                try {
                    produce();
                } catch (Exception e) {
                    LOGGER.error("Erreur lors de la production", e);
                }
        });

        // consommation
        if (getScenario() != 3) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    consume();
                } catch (Exception e) {
                    LOGGER.error("Erreur lors de la consommation", e);
                }
            });
        }
    }

    public static void produce()
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        LOGGER.info("Producteur lance'");

        // preparation de l'URL UAA
        List<NameValuePair> urlParameters = Utils.getUrlParameters();

        // preparation de TLS pour les appels a UAA
        SSLContext sslContext = Utils.getSSLContext();

        // demande a UAA d'un jeton OAuth
        String accessToken = Utils.getAccessToken(sslContext, urlParameters);
        LOGGER.info("Access token = [{}]", accessToken);

        // connexion a RabbitMQ
        ConnectionFactory connectionFactory = Utils.getConnectionFactory(sslContext, accessToken);
        try (Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {

            // instruction au channel d'envoyer des acquittements (publisher confirms)
            AMQP.Confirm.SelectOk selectOk = channel.confirmSelect();
            LOGGER.info("selectOk = [{}]", selectOk);

            // ajout au channel des fonctions de traitement des acquittements
            channel.addConfirmListener(
                    (deliveryTag, multiple) -> LOGGER.info("ack for {}, multiple = {}", deliveryTag, multiple),
                    (deliveryTag, multiple) -> LOGGER.info("nack for {}, multiple = {}", deliveryTag, multiple));
            channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) ->
                    LOGGER.info("Retour pour message [{}] : replyCode = [{}], replyText = [{}], exchange = [{}], routing key = [{}]",
                            body, replyCode, replyText, exchange, routingKey));
            LOGGER.info("Callbacks ajoutes");

            // envoi des messages a RabbitMQ
            IntStream.range(1, getNbIterations() + 1).forEach(iteration -> {
                // a chaque iteration, les scenarios 1 et 3 envoient 1 message, tandis que le scenario 2
                // envoie un nombre croissant de messages
                IntStream.range(1, getNbMessages(iteration) + 1).forEach(i -> {
                    String message = createNextMessage();
                    LOGGER.warn("Taille = {}", message.length());
                    try {
                        channel.basicPublish(
                                getProperty("rabbitmq.exchange"),
                                getProperty("rabbitmq.routing-key"),
                                null,
                                message.getBytes(Charset.defaultCharset()));
                        LOGGER.info("Message {} envoye", NB_SENT_MESSAGES);
                    } catch (IOException e) {
                        LOGGER.error("Erreur recue lors de la production du message. Iteration = {}", iteration, e);
                    }
                });

                // attente entre 2 messages (ou entre 2 salves de messages)
                Utils.wait(getInterval(), "producteur");
            });

            // attente des derniers acquittements (producer confirms)
            Utils.wait(3 * 1000, "acquittements de RabbitMQ au producteur");
        } catch (Exception e) {
            LOGGER.error("Erreur recue", e);
        }
    }

    public static void consume() throws NoSuchAlgorithmException, KeyManagementException, IOException {
        LOGGER.info("Consommateur lance'");

        // preparation de l'URL UAA
        List<NameValuePair> urlParameters = Utils.getUrlParameters();

        // preparation de TLS pour les appels a UAA
        SSLContext sslContext = Utils.getSSLContext();

        // demande a UAA d'un jeton OAuth
        String accessToken = Utils.getAccessToken(sslContext, urlParameters);
        LOGGER.info("Access token = [{}]", accessToken);

        // connexion a RabbitMQ
        ConnectionFactory connectionFactory = Utils.getConnectionFactory(sslContext, accessToken);
        try (Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {

            // instruction au channel d'envoyer des acquittements (consumer acknowledgements)
            AMQP.Confirm.SelectOk selectOk = channel.confirmSelect();
            LOGGER.info("selectOk = [{}]", selectOk);

            // preparation des fonctions de retour
            DeliverCallback deliverCallback = (consumerTag, delivery) ->
                LOGGER.info("Message recu : [{}]", new String(delivery.getBody(), StandardCharsets.UTF_8).substring(0, 20));
            CancelCallback cancelCallback = consumerTag ->
                LOGGER.info("consumerTag [{}] annule", consumerTag);

            // attente des messages de RabbitMQ
            channel.basicConsume(getProperty("rabbitmq.queue"), true, deliverCallback, cancelCallback);

            // attente que le producteur ait tout produit
            Utils.wait(getNbIterations() * (getInterval() + 50), "consommateur");
        } catch (Exception e) {
            LOGGER.error("Erreur recue", e);
        }
    }

}

