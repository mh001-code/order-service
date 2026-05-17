package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.port.in.CreateOrderUseCase;
import com.orderprocessing.order.service.application.port.in.OrderItemInput;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.InvalidOrderException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class CreateOrderService implements CreateOrderUseCase {

    private final OrderPersistencePort persistencePort;

    @Override
    public Order execute(UUID customerId, List<OrderItemInput> items) {
        BigDecimal totalAmount = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(BigDecimal.ONE) < 0) {
            throw new InvalidOrderException(
                    "Order total must be at least R$1.00, but was: R$" + totalAmount);
        }

        List<OrderItem> orderItems = items.stream().map(input -> {
            OrderItem item = new OrderItem();
            item.setProductId(input.productId());
            item.setProductName(input.productName());
            item.setQuantity(input.quantity());
            item.setUnitPrice(input.unitPrice());
            return item;
        }).toList();

        Order order = Order.create(customerId, orderItems, totalAmount);
        Order saved = persistencePort.save(order);

        saved.confirm();
        return persistencePort.save(saved);
    }
}
