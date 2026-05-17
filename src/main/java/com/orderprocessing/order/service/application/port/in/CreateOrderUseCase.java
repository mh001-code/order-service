package com.orderprocessing.order.service.application.port.in;

import com.orderprocessing.order.service.domain.model.Order;

import java.util.List;
import java.util.UUID;

public interface CreateOrderUseCase {
    Order execute(UUID customerId, List<OrderItemInput> items);
}
