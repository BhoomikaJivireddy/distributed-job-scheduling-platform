package com.jobplatform.worker.messaging;

import com.jobplatform.common.messaging.MessagingNames;
import com.jobplatform.worker.execution.JobProcessor;
import com.jobplatform.worker.execution.ProcessingResult;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class JobMessageListener {

    private static final Logger log = LoggerFactory.getLogger(JobMessageListener.class);

    private final JobProcessor jobProcessor;

    public JobMessageListener(JobProcessor jobProcessor) {
        this.jobProcessor = jobProcessor;
    }

    @RabbitListener(queues = MessagingNames.JOB_QUEUE)
    public void onMessage(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        boolean redelivered = message.getMessageProperties().isRedelivered();

        Long jobId = parseJobId(message);
        if (jobId == null) {
            log.error("Malformed job message with delivery tag {}; sending to the dead letter queue", deliveryTag);
            rejectToDeadLetterQueue(channel, deliveryTag);
            return;
        }

        ProcessingResult result;
        try {
            result = jobProcessor.process(jobId);
        } catch (RuntimeException exception) {
            log.error("Job {} could not be processed (redelivered={}): {}", jobId, redelivered, exception.toString());
            if (redelivered) {
                rejectToDeadLetterQueue(channel, deliveryTag);
            } else {
                requeueOnce(channel, deliveryTag);
            }
            return;
        }

        if (result.requiresDeadLetter()) {
            rejectToDeadLetterQueue(channel, deliveryTag);
        } else {
            acknowledge(channel, deliveryTag);
        }
    }

    private void acknowledge(Channel channel, long deliveryTag) throws IOException {
        channel.basicAck(deliveryTag, false);
    }

    private void rejectToDeadLetterQueue(Channel channel, long deliveryTag) throws IOException {
        channel.basicNack(deliveryTag, false, false);
    }

    private void requeueOnce(Channel channel, long deliveryTag) throws IOException {
        channel.basicNack(deliveryTag, false, true);
    }

    private Long parseJobId(Message message) {
        try {
            return Long.parseLong(new String(message.getBody(), StandardCharsets.UTF_8).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
