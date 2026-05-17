package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.port.in.CancelOrderUseCase;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.OrderAlreadyCancelledException;
import com.orderprocessing.order.service.domain.exception.OrderNotFoundException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CancelOrderService implements CancelOrderUseCase {

    private final OrderPersistencePort persistencePort;

    @Override
    public Order execute(UUID orderId) {
        Order order = persistencePort.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderAlreadyCancelledException(orderId);
        }

        order.cancel();
        return persistencePort.save(order);
    }
}
