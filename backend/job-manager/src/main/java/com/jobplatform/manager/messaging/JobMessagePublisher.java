package com.jobplatform.manager.messaging;

import com.jobplatform.common.messaging.MessagingNames;
import com.jobplatform.manager.config.ManagerProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class JobMessagePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ManagerProperties properties;

    public JobMessagePublisher(RabbitTemplate rabbitTemplate, ManagerProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    public void publish(Long jobId, int priority) {
        Message message = MessageBuilder
                .withBody(String.valueOf(jobId).getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setPriority(priority)
                .build();

        CorrelationData correlation = new CorrelationData(String.valueOf(jobId));
        rabbitTemplate.send(MessagingNames.JOB_EXCHANGE, MessagingNames.JOB_ROUTING_KEY, message, correlation);
        awaitBrokerConfirm(jobId, correlation);
    }

    private void awaitBrokerConfirm(Long jobId, CorrelationData correlation) {
        try {
            CorrelationData.Confirm confirm =
                    correlation.getFuture().get(properties.publishConfirmTimeoutMs(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new MessagePublishException("Broker rejected message for job " + jobId + ": " + confirm.getReason());
            }
            if (correlation.getReturned() != null) {
                throw new MessagePublishException("Message for job " + jobId + " could not be routed to a queue");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new MessagePublishException("Interrupted while waiting for broker confirm for job " + jobId, exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new MessagePublishException("No broker confirm received for job " + jobId, exception);
        }
    }
}
