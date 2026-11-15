package com.shopsphere.orderservice.messaging;

import java.util.function.Consumer;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

import com.shopsphere.orderservice.config.OrderProperties;
import com.shopsphere.orderservice.exception.NonRetryableEventException;

/**
 * Failure policy for every inbound listener: transient errors are retried with exponential backoff; when the retries are
 * used up, or the error is a {@link NonRetryableEventException}, the record is published to "{topic}.DLT"
 * and the partition moves on (one poison message never blocks the topic).
 */
public final class KafkaErrorHandlers {

    private KafkaErrorHandlers() {
    }

    public static DefaultErrorHandler create(KafkaOperations<String, String> kafkaTemplate,
                                             OrderProperties.Inbound.Retry retry,
                                             Consumer<ConsumerRecord<?, ?>> onDeadLetter) {
        DeadLetterPublishingRecoverer deadLetters = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", -1)); // -1: any partition

        ConsumerRecordRecoverer recoverer = (record, exception) -> {
            onDeadLetter.accept(record);
            deadLetters.accept(record, exception);
        };

        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(retry.maxRetries());
        backOff.setInitialInterval(retry.initialInterval().toMillis());
        backOff.setMultiplier(retry.multiplier());
        backOff.setMaxInterval(retry.maxInterval().toMillis());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(NonRetryableEventException.class);
        return handler;
    }
}
