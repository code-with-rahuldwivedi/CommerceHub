package com.shopsphere.orderservice.messaging.inbound;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** One listener for all envelope topics listed in "orders.inbound.topics" (comma separated). */
@Component
@ConditionalOnProperty(prefix = "orders.inbound", name = "enabled", havingValue = "true")
public class InboundEventListener {

    private final InboundEventDispatcher dispatcher;

    public InboundEventListener(InboundEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @KafkaListener(
            topics = "#{'${orders.inbound.topics}'.split(',')}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onMessage(ConsumerRecord<String, String> record) {
        dispatcher.dispatch(record.topic(), record.value());
    }
}
