package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.event.OrderCreatedEvent;
import com.orderprocessing.order.service.application.port.in.CreateOrderUseCase;
import com.orderprocessing.order.service.application.port.in.OrderItemInput;
import com.orderprocessing.order.service.application.port.out.OrderEventPublisherPort;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.InvalidOrderException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderItem;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
    private final OrderEventPublisherPort eventPublisherPort;
    private final MeterRegistry meterRegistry;

    @Override
    public Order execute(UUID customerId, List<OrderItemInput> items) {
        Timer.Sample sample = Timer.start(meterRegistry);

        BigDecimal totalAmount = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalAmount.compareTo(BigDecimal.ONE) < 0) {
            Counter.builder("orders.created.total")
                    .tag("status", "invalid")
                    .register(meterRegistry)
                    .increment();
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
        Order confirmed = persistencePort.save(saved);

        List<OrderCreatedEvent.Item> eventItems = items.stream()
                .map(i -> new OrderCreatedEvent.Item(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        eventPublisherPort.publishOrderCreated(
                new OrderCreatedEvent(confirmed.getId(), confirmed.getCustomerId(), eventItems, confirmed.getTotalAmount()));

        Counter.builder("orders.created.total")
                .tag("status", "success")
                .register(meterRegistry)
                .increment();

        sample.stop(Timer.builder("orders.create.duration")
                .description("Tempo de criação de pedido")
                .register(meterRegistry));

        return confirmed;
    }
}
