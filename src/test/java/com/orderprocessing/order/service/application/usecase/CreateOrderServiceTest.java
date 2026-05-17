package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.event.OrderCreatedEvent;
import com.orderprocessing.order.service.application.port.in.OrderItemInput;
import com.orderprocessing.order.service.application.port.out.OrderEventPublisherPort;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.InvalidOrderException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateOrderServiceTest {

    @Mock
    private OrderPersistencePort persistencePort;

    @Mock
    private OrderEventPublisherPort eventPublisherPort;

    @InjectMocks
    private CreateOrderService createOrderService;

    @Test
    void shouldCreateOrderSuccessfully() {
        UUID customerId = UUID.randomUUID();
        List<OrderItemInput> items = List.of(
                new OrderItemInput(UUID.randomUUID(), "Product A", 2, new BigDecimal("10.00"))
        );
        when(persistencePort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = createOrderService.execute(customerId, items);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(result.getCustomerId()).isEqualTo(customerId);
        verify(persistencePort, times(2)).save(any(Order.class));
        verify(eventPublisherPort).publishOrderCreated(any(OrderCreatedEvent.class));
    }

    @Test
    void shouldThrowInvalidOrderExceptionWhenTotalBelowMinimum() {
        UUID customerId = UUID.randomUUID();
        List<OrderItemInput> items = List.of(
                new OrderItemInput(UUID.randomUUID(), "Cheap Item", 1, new BigDecimal("0.50"))
        );

        assertThatThrownBy(() -> createOrderService.execute(customerId, items))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("R$1.00");

        verifyNoInteractions(persistencePort, eventPublisherPort);
    }

    @Test
    void shouldCalculateTotalCorrectlyForMultipleItems() {
        UUID customerId = UUID.randomUUID();
        List<OrderItemInput> items = List.of(
                new OrderItemInput(UUID.randomUUID(), "Item A", 3, new BigDecimal("5.00")),
                new OrderItemInput(UUID.randomUUID(), "Item B", 2, new BigDecimal("7.50"))
        );
        when(persistencePort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = createOrderService.execute(customerId, items);

        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("30.00"));
    }
}
