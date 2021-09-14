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

/**
 * Envoi et reception d'un lot de messages de RabbitMQ.
 */
public class LoadProducerConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoadProducerConsumer.class);

    private static final int NB_MESSAGES = 2;

    /**
     * Lancement en parallele de la production et de la consommation.
     */
    public static void main(String[] argv) {
        // production
        Executors.newCachedThreadPool().submit(() -> {
                try {
                    produce(NB_MESSAGES);
                } catch (Exception e) {
                    LOGGER.error("Erreur lors de la production", e);
                }
        });

        // consommation
        Executors.newCachedThreadPool().submit(() -> {
                try {
                    consume();
                } catch (Exception e) {
                    LOGGER.error("Erreur lors de la consommation", e);
                }
        });
    }

    public static void produce(int nbMessages)
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
            IntStream.range(1, nbMessages + 1).forEach(i -> {
                String message = "Message " + i;
                try {
                    channel.basicPublish(Utils.RABBITMQ_EXCHANGE, Utils.RABBITMQ_ROUTING_KEY, null, message.getBytes(Charset.defaultCharset()));
                    LOGGER.info("Message envoye : [{}]", message);
                } catch (IOException e) {
                    LOGGER.error("Erreur recue lors de la production du message {}", i, e);
                }
            });

            // attente des derniers acquittements (producer confirms)
            Utils.wait(3, "acquittements de RabbitMQ");
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
                LOGGER.info("Message recu : [{}]", new String(delivery.getBody(), StandardCharsets.UTF_8));
            CancelCallback cancelCallback = consumerTag ->
                LOGGER.info("consumerTag [{}] annule", consumerTag);

            // attente des messages de RabbitMQ
            channel.basicConsume(Utils.RABBITMQ_QUEUE_NAME, true, deliverCallback, cancelCallback);

            // attente de l'acquittement
            Utils.wait(2, "consommation des derniers messages");
        } catch (Exception e) {
            LOGGER.error("Erreur recue", e);
        }
    }

}

