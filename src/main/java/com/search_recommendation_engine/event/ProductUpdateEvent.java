package com.search_recommendation_engine.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ProductUpdateEvent(
        Long productId,
        String eventType,   // "PRICE_CHANGE", "STOCK_CHANGE", "CREATED", "DELETED"
        BigDecimal newPrice,
        Integer newStockQuantity,
        LocalDateTime timestamp
) {}