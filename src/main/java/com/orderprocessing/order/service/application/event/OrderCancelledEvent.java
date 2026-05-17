package com.orderprocessing.order.service.application.event;

import java.util.UUID;

public record OrderCancelledEvent(UUID orderId, UUID customerId, String reason) {}
