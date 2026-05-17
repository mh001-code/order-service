package com.orderprocessing.order.service.application.port.out;

import com.orderprocessing.order.service.domain.model.Order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderPersistencePort {
    Order save(Order order);
    Optional<Order> findById(UUID id);
    List<Order> findByCustomerId(UUID customerId);
}
