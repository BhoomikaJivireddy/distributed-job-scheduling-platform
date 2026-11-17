package com.jobplatform.common.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {

    @Bean
    public DirectExchange jobExchange() {
        return ExchangeBuilder.directExchange(MessagingNames.JOB_EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(MessagingNames.DEAD_LETTER_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue jobQueue() {
        return QueueBuilder.durable(MessagingNames.JOB_QUEUE)
                .maxPriority(MessagingNames.MAX_PRIORITY)
                .deadLetterExchange(MessagingNames.DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(MessagingNames.DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(MessagingNames.DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding jobBinding() {
        return BindingBuilder.bind(jobQueue()).to(jobExchange()).with(MessagingNames.JOB_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange())
                .with(MessagingNames.DEAD_LETTER_ROUTING_KEY);
    }
}
