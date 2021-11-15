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
 * Production and consumption of RabbitMQ messages.
 */
public class ProducerConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProducerConsumer.class);

    /**
     * Starts the producer and the producer. They run concurrently.
     */
    public static void main(String[] args) {
        Utils.readProperties(args);
        LOGGER.info("Scenario {}", getScenario());

        // production
        Executors.newCachedThreadPool().submit(() -> {
                try {
                    produce();
                } catch (Exception e) {
                    LOGGER.error("Error during message production", e);
                }
        });

        // consommation
        if (getScenario() != 3) {
            Executors.newCachedThreadPool().submit(() -> {
                try {
                    consume();
                } catch (Exception e) {
                    LOGGER.error("Error during message consumption", e);
                }
            });
        }
    }

    public static void produce()
            throws NoSuchAlgorithmException, KeyManagementException, IOException {
        LOGGER.info("Producer started'");

        // set up the UAA URL
        List<NameValuePair> urlParameters = Utils.getUrlParameters();

        // set up TLS for the calls to UAA
        SSLContext sslContext = Utils.getSSLContext();

        // request an OAuth token from UAA
        String accessToken = Utils.getAccessToken(sslContext, urlParameters);
        LOGGER.info("Producer's access token = [{}]", accessToken);

        // connect to RabbitMQ
        ConnectionFactory connectionFactory = Utils.getConnectionFactory(sslContext, accessToken);
        try (Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {

            // instruct the channel to send publisher confirms
            AMQP.Confirm.SelectOk selectOk = channel.confirmSelect();
            LOGGER.info("selectOk = [{}]", selectOk);

            // instrument the channel with functions to handle the publisher confirms
            channel.addConfirmListener(
                    (deliveryTag, multiple) -> LOGGER.info("ack for {}, multiple = {}", deliveryTag, multiple),
                    (deliveryTag, multiple) -> LOGGER.info("nack for {}, multiple = {}", deliveryTag, multiple));
            channel.addReturnListener((replyCode, replyText, exchange, routingKey, properties, body) ->
                    LOGGER.info("Response for message [{}] : replyCode = [{}], replyText = [{}], exchange = [{}], routing key = [{}]",
                            body, replyCode, replyText, exchange, routingKey));
            LOGGER.info("The callbacks were added");

            // send the messages to RabbitMQ
            IntStream.range(1, getNbIterations() + 1).forEach(iteration -> {
                LOGGER.info("Iteration {} of {}", iteration, getNbIterations());

                // at every iteration, scenarios 1 and 3 publish one message, whereas scenario 2 publishes
                // an increasing number of messages
                IntStream.range(1, getNbMessages(iteration) + 1).forEach(i -> {
                    String message = createNextMessage();
                    try {
                        channel.basicPublish(
                                getProperty("rabbitmq.exchange"),
                                getProperty("rabbitmq.routing-key"),
                                null,
                                message.getBytes(Charset.defaultCharset()));
                        LOGGER.info("Message {} sent", NB_SENT_MESSAGES);
                    } catch (IOException e) {
                        LOGGER.error("Error caught during the message production. Iteration = {}", iteration, e);
                    }
                });

                // wait between 2 messages (scenario 2: between 2 batches of messages)
                Utils.wait(getInterval(), "producer");
            });

            // before exiting, wait for the last producer confirms
            Utils.wait(3 * 1000, "producer confirms");
        } catch (Exception e) {
            LOGGER.error("Error caught", e);
        }
    }

    public static void consume() throws NoSuchAlgorithmException, KeyManagementException, IOException {
        LOGGER.info("Consumer started");

        // set up the UAA URL
        List<NameValuePair> urlParameters = Utils.getUrlParameters();

        // set up TLS for the calls to UAA
        SSLContext sslContext = Utils.getSSLContext();

        // request an OAuth token from UAA
        String accessToken = Utils.getAccessToken(sslContext, urlParameters);
        LOGGER.info("Consumer's access token = [{}]", accessToken);

        // connect to RabbitMQ
        ConnectionFactory connectionFactory = Utils.getConnectionFactory(sslContext, accessToken);
        try (Connection connection = connectionFactory.newConnection();
             Channel channel = connection.createChannel()) {

            // instruct the channel to send consumer acknowledgements
            AMQP.Confirm.SelectOk selectOk = channel.confirmSelect();
            LOGGER.info("selectOk = [{}]", selectOk);

            // set up the callbacks to handle the consumer acknowledgements
            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                String msg = new String(delivery.getBody(), StandardCharsets.UTF_8);
                LOGGER.info("Message received: [{} ...] (size = {} bytes)", msg.substring(0, 20), msg.length());
            };
            CancelCallback cancelCallback = consumerTag ->
                LOGGER.info("ConsumerTag [{}] canceled", consumerTag);

            // wait for messages from RabbitMQ
            channel.basicConsume(getProperty("rabbitmq.queue"), true, deliverCallback, cancelCallback);

            // wait the producer is done with sending messages
            Utils.wait(getNbIterations() * (getInterval() + getConsumerWaitMargin()), "consommateur");
        } catch (Exception e) {
            LOGGER.error("Error caught", e);
        }
    }

    /**
     * Additional wait time for the consumer before the consumer exits.
     * It is used for accounting the fact that, during an iteration, the production of the message (or messages) is
     * not instantaneous, so the consumer cannot exit after (nbIterations * interval) milliseconds.
     * Specifically, this margin is required for scenario 2, because scenario 2 produces several messages per iteration.
     * It is expressed in milliseconds at every iteration.
     */
    private static int getConsumerWaitMargin() {
        if (getScenario() == 1 || getScenario() == 3) {
            return 20;
        } else {
            return 2 * getNbIterations();
        }
    }

}
