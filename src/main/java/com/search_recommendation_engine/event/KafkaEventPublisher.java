package com.search_recommendation_engine.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaEventPublisher.class);
    private static final String TOPIC = "product-updates";

    private final KafkaTemplate<String, ProductUpdateEvent> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, ProductUpdateEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(ProductUpdateEvent event) {
        // key = productId as a string — ensures all events for the same product
        // land on the same partition, preserving per-product ordering
        kafkaTemplate.send(TOPIC, String.valueOf(event.productId()), event);
        log.info("Published event: {}", event);
    }
}