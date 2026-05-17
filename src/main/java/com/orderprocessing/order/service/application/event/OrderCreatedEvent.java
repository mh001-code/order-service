package com.orderprocessing.order.service.application.event;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        UUID orderId,
        UUID customerId,
        List<Item> items,
        BigDecimal totalAmount
) {
    public record Item(UUID productId, String productName, int quantity, BigDecimal unitPrice) {}
}
