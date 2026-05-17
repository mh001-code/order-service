package com.orderprocessing.order.service.application.port.in;

import com.orderprocessing.order.service.domain.model.Order;

import java.util.UUID;

public interface CancelOrderUseCase {
    Order execute(UUID orderId);
}
