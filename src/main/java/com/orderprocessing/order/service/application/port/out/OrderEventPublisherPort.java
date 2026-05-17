package com.orderprocessing.order.service.application.port.out;

import com.orderprocessing.order.service.application.event.OrderCancelledEvent;
import com.orderprocessing.order.service.application.event.OrderCreatedEvent;

public interface OrderEventPublisherPort {
    void publishOrderCreated(OrderCreatedEvent event);
    void publishOrderCancelled(OrderCancelledEvent event);
}
