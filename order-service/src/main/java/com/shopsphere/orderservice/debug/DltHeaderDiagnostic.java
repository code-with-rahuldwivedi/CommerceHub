package com.shopsphere.orderservice.debug;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.util.Properties;

public class DltHeaderDiagnostic {

    public static void main(String[] args) {
        Properties props = new Properties();

        props.put(
                "bootstrap.servers",
                "localhost:9092"
        );
        props.put(
                "group.id",
                "dlt-header-diagnostic-" + System.currentTimeMillis()
        );
        props.put(
                "key.deserializer",
                StringDeserializer.class.getName()
        );
        props.put(
                "value.deserializer",
                StringDeserializer.class.getName()
        );
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "false");

        try (KafkaConsumer<String, String> consumer =
                     new KafkaConsumer<>(props)) {

            consumer.subscribe(
                    Collections.singletonList("inventory-events.DLT")
            );

            long deadline =
                    System.currentTimeMillis() + 15000;

            boolean found = false;

            while (System.currentTimeMillis() < deadline && !found) {
                var records = consumer.poll(Duration.ofMillis(500));

                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("\n=== DLT RECORD ===");
                    System.out.println("DLT partition: " + record.partition());
                    System.out.println("DLT offset: " + record.offset());
                    System.out.println("Payload: " + record.value());

                    System.out.println("\n=== HEADERS ===");

                    for (Header header : record.headers()) {
                        byte[] value = header.value();

                        System.out.printf(
                                "Header: %s | isNull: %s | length: %s | value: %s%n",
                                header.key(),
                                value == null,
                                value == null ? "null" : value.length,
                                value == null
                                        ? "null"
                                        : java.util.Arrays.toString(value)
                        );
                    }
                    found = true;
                    break;
                }
            }

            if (!found) {
                System.out.println(
                        "No DLT record found within 15 seconds."
                );
            }
        }
    }
}
