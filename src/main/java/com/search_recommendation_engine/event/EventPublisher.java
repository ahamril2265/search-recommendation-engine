package com.search_recommendation_engine.event;

public interface EventPublisher {
    void publish(ProductUpdateEvent event);
}