package com.jobplatform.manager.messaging;

import com.jobplatform.common.messaging.MessagingNames;
import com.jobplatform.manager.config.ManagerProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class JobMessagePublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private JobMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new JobMessagePublisher(rabbitTemplate, new ManagerProperties(50, 60, 50, 20, 365, 200));
    }

    private void brokerAnswersWith(boolean ack, String reason) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate).send(eq(MessagingNames.JOB_EXCHANGE), eq(MessagingNames.JOB_ROUTING_KEY),
                any(Message.class), any(CorrelationData.class));
    }

    @Test
    void messageCarriesOnlyTheJobIdAndIsPersistentWithPriority() {
        brokerAnswersWith(true, null);

        publisher.publish(42L, 8);

        ArgumentCaptor<Message> message = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(MessagingNames.JOB_EXCHANGE), eq(MessagingNames.JOB_ROUTING_KEY),
                message.capture(), any(CorrelationData.class));
        assertThat(new String(message.getValue().getBody(), StandardCharsets.UTF_8)).isEqualTo("42");
        assertThat(message.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(message.getValue().getMessageProperties().getPriority()).isEqualTo(8);
    }

    @Test
    void brokerNackIsReportedAsAPublishFailure() {
        brokerAnswersWith(false, "queue full");

        assertThatThrownBy(() -> publisher.publish(42L, 5))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("queue full");
    }

    @Test
    void missingConfirmTimesOutAsAPublishFailure() {
        assertThatThrownBy(() -> publisher.publish(42L, 5))
                .isInstanceOf(MessagePublishException.class)
                .hasMessageContaining("No broker confirm");
    }
}
