package com.shopsphere.orderservice.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.shopsphere.orderservice.consumer.InventoryResultConsumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@EmbeddedKafka(
        partitions = 1,
        topics = {"non-retry-test", "non-retry-test.DLT"}
)
@SpringJUnitConfig(NonRetryableDltTest.TestConfig.class)
class NonRetryableDltTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Configuration(proxyBeanMethods = false)
    static class TestConfig {

        @Bean
        DefaultKafkaProducerFactory<String, String> testProducerFactory(
                EmbeddedKafkaBroker broker) {

            return new DefaultKafkaProducerFactory<>(
                    KafkaTestUtils.producerProps(broker));
        }

        @Bean
        KafkaTemplate<String, String> testKafkaTemplate(
                DefaultKafkaProducerFactory<String, String> factory) {

            return new KafkaTemplate<>(factory);
        }
    }

    @Test
    void shouldSendNonRetryableExceptionDirectlyToDlt()
            throws Exception {

        var errorHandler =
                new KafkaConsumerConfig()
                        .inventoryResultErrorHandler(kafkaTemplate);

        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps(
                        "non-retry-test-group",
                        "false",
                        embeddedKafka);

        consumerProps.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest");

        var consumerFactory =
                new DefaultKafkaConsumerFactory<String, String>(
                        consumerProps,
                        new StringDeserializer(),
                        new StringDeserializer());

        AtomicInteger attempts = new AtomicInteger();

        ContainerProperties containerProperties =
                new ContainerProperties("non-retry-test");

        var container =
                new KafkaMessageListenerContainer<String, String>(
                        consumerFactory,
                        containerProperties);

        container.setCommonErrorHandler(errorHandler);

        container.setupMessageListener(
                (MessageListener<String, String>) record -> {
                    attempts.incrementAndGet();

                    throw new InventoryResultConsumer.MissingOrderException(
                            "Test: order does not exist");
                });

        try {
            container.start();

            ContainerTestUtils.waitForAssignment(container, 1);

            String payload = "non-retryable-verification-message";

            kafkaTemplate.send("non-retry-test", payload)
                    .get(10, TimeUnit.SECONDS);

            Map<String, Object> dltProps =
                    KafkaTestUtils.consumerProps(
                            "non-retry-test-dlt-reader",
                            "false",
                            embeddedKafka);

            dltProps.put(
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                    "earliest");

            try (Consumer<String, String> dltConsumer =
                         new KafkaConsumer<>(
                                 dltProps,
                                 new StringDeserializer(),
                                 new StringDeserializer())) {

                dltConsumer.subscribe(
                        List.of("non-retry-test.DLT"));

                org.apache.kafka.clients.consumer.ConsumerRecord<
                        String, String> recoveredRecord = null;

                long deadline = System.nanoTime()
                        + Duration.ofSeconds(15).toNanos();

                while (System.nanoTime() < deadline
                        && recoveredRecord == null) {

                    var records =
                            dltConsumer.poll(Duration.ofMillis(250));

                    for (var record : records) {
                        if (payload.equals(record.value())) {
                            recoveredRecord = record;
                            break;
                        }
                    }
                }

                assertNotNull(
                        recoveredRecord,
                        "Expected message directly in non-retry-test.DLT");

                assertEquals(
                        1,
                        attempts.get(),
                        "Non-retryable exception must not be retried");

                Header originalTopic =
                        recoveredRecord.headers()
                                .lastHeader("kafka_dlt-original-topic");

                assertNotNull(
                        originalTopic,
                        "Original topic header must be present");

                assertEquals(
                        "non-retry-test",
                        new String(
                                originalTopic.value(),
                                StandardCharsets.UTF_8));
            }
        } finally {
            container.stop();
        }
    }
}
