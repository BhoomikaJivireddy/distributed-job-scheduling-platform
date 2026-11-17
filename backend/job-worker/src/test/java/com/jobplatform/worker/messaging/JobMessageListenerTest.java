package com.jobplatform.worker.messaging;

import com.jobplatform.worker.execution.JobProcessor;
import com.jobplatform.worker.execution.ProcessingResult;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobMessageListenerTest {

    private static final long TAG = 17L;

    @Mock
    private JobProcessor jobProcessor;

    @Mock
    private Channel channel;

    private JobMessageListener listener;

    @BeforeEach
    void setUp() {
        listener = new JobMessageListener(jobProcessor);
    }

    private Message message(String body, boolean redelivered) {
        MessageProperties properties = new MessageProperties();
        properties.setDeliveryTag(TAG);
        properties.setRedelivered(redelivered);
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    @Test
    void completedJobIsAcknowledgedAfterProcessing() throws Exception {
        when(jobProcessor.process(42L)).thenReturn(ProcessingResult.COMPLETED);

        listener.onMessage(message("42", false), channel);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void scheduledRetryIsAcknowledgedBecauseTheDatabaseOwnsTheRetry() throws Exception {
        when(jobProcessor.process(42L)).thenReturn(ProcessingResult.RETRY_SCHEDULED);

        listener.onMessage(message("42", false), channel);

        verify(channel).basicAck(TAG, false);
    }

    @Test
    void duplicateDeliveryIsAcknowledgedAndDropped() throws Exception {
        when(jobProcessor.process(42L)).thenReturn(ProcessingResult.DISCARDED);

        listener.onMessage(message("42", true), channel);

        verify(channel).basicAck(TAG, false);
    }

    @Test
    void deadJobIsRejectedWithoutRequeueSoTheBrokerRoutesItToTheDeadLetterQueue() throws Exception {
        when(jobProcessor.process(42L)).thenReturn(ProcessingResult.DEAD);

        listener.onMessage(message("42", false), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(TAG, false);
    }

    @Test
    void infrastructureFailureOnFirstDeliveryRequeuesTheMessage() throws Exception {
        when(jobProcessor.process(42L)).thenThrow(new IllegalStateException("database down"));

        listener.onMessage(message("42", false), channel);

        verify(channel).basicNack(TAG, false, true);
    }

    @Test
    void infrastructureFailureOnRedeliveryDeadLettersInsteadOfLoopingForever() throws Exception {
        when(jobProcessor.process(42L)).thenThrow(new IllegalStateException("database down"));

        listener.onMessage(message("42", true), channel);

        verify(channel).basicNack(TAG, false, false);
    }

    @Test
    void malformedMessageIsDeadLetteredWithoutProcessing() throws Exception {
        listener.onMessage(message("not-a-job-id", false), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(jobProcessor, never()).process(org.mockito.ArgumentMatchers.anyLong());
    }
}
