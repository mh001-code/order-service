package com.orderprocessing.order.service.domain.exception;

import java.util.UUID;

public class OrderAlreadyCancelledException extends RuntimeException {
    public OrderAlreadyCancelledException(UUID id) {
        super("Order is already cancelled: " + id);
    }
}
