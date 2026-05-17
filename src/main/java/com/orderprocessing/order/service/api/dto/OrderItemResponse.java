package com.orderprocessing.order.service.api.dto;

import com.orderprocessing.order.service.domain.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getProductId(),
                item.getProductName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
        );
    }
}
