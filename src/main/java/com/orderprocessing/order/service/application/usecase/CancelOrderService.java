package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.event.OrderCancelledEvent;
import com.orderprocessing.order.service.application.port.in.CancelOrderUseCase;
import com.orderprocessing.order.service.application.port.out.OrderEventPublisherPort;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.OrderAlreadyCancelledException;
import com.orderprocessing.order.service.domain.exception.OrderNotFoundException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderPersistencePort persistencePort;
    private final OrderEventPublisherPort eventPublisherPort;
    private final MeterRegistry meterRegistry;

    @Override
    public Order execute(UUID orderId) {
        Order order = persistencePort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        order.cancel();
        Order cancelled = persistencePort.save(order);

        eventPublisherPort.publishOrderCancelled(
                new OrderCancelledEvent(cancelled.getId(), cancelled.getCustomerId(), "Cancelled by customer"));

        Counter.builder("orders.cancelled.total")
                .description("Total de pedidos cancelados")
                .register(meterRegistry)
                .increment();

        return cancelled;
    }
}
