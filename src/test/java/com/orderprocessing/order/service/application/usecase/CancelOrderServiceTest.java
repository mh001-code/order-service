package com.orderprocessing.order.service.application.usecase;

import com.orderprocessing.order.service.application.event.OrderCancelledEvent;
import com.orderprocessing.order.service.application.port.out.OrderEventPublisherPort;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.OrderAlreadyCancelledException;
import com.orderprocessing.order.service.domain.exception.OrderNotFoundException;
import com.orderprocessing.order.service.domain.model.Order;
import com.orderprocessing.order.service.domain.model.OrderStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    @Mock
    private OrderPersistencePort persistencePort;

    @Mock
    private OrderEventPublisherPort eventPublisherPort;

    @InjectMocks
    private CancelOrderService cancelOrderService;

    @Test
    void shouldCancelOrderSuccessfully() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), List.of(), BigDecimal.TEN);
        order.confirm();
        when(persistencePort.findById(orderId)).thenReturn(Optional.of(order));
        when(persistencePort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        Order result = cancelOrderService.execute(orderId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(eventPublisherPort).publishOrderCancelled(any(OrderCancelledEvent.class));
    }

    @Test
    void shouldThrowOrderNotFoundExceptionWhenOrderDoesNotExist() {
        UUID orderId = UUID.randomUUID();
        when(persistencePort.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cancelOrderService.execute(orderId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());

        verifyNoInteractions(eventPublisherPort);
    }

    @Test
    void shouldThrowWhenOrderIsAlreadyCancelled() {
        UUID orderId = UUID.randomUUID();
        Order order = Order.create(UUID.randomUUID(), List.of(), BigDecimal.TEN);
        order.cancel();
        when(persistencePort.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> cancelOrderService.execute(orderId))
                .isInstanceOf(OrderAlreadyCancelledException.class);

        verify(persistencePort, never()).save(any());
        verifyNoInteractions(eventPublisherPort);
    }
}
