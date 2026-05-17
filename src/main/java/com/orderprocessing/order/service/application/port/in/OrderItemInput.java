package com.orderprocessing.order.service.application.port.in;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemInput(UUID productId, String productName, int quantity, BigDecimal unitPrice) {}
