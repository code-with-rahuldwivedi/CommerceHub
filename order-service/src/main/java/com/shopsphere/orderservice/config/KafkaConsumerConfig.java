package com.shopsphere.orderservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;

import com.shopsphere.orderservice.messaging.KafkaErrorHandlers;
import com.shopsphere.orderservice.metrics.OrderMetrics;

/**
 * Spring Boot's auto-configured listener container factory picks up a single CommonErrorHandler bean,
 * so every @KafkaListener in this service gets retry-with-backoff and a dead-letter topic.
 */
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler inboundErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                   OrderProperties properties,
                                                   OrderMetrics metrics) {
        return KafkaErrorHandlers.create(kafkaTemplate, properties.inbound().retry(),
                record -> metrics.deadLetter(record.topic()));
    }
}
