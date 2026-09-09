package com.search_recommendation_engine.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ProductUpdateEventBridge {

    private final EventPublisher eventPublisher;

    public ProductUpdateEventBridge(EventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductUpdateCommitted(ProductUpdateTransactionEvent event) {
        eventPublisher.publish(event.payload());
    }
}