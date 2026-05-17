package com.orderprocessing.order.service.infrastructure.messaging;

import com.orderprocessing.order.service.application.event.OrderCancelledEvent;
import com.orderprocessing.order.service.application.event.OrderCreatedEvent;
import com.orderprocessing.order.service.infrastructure.config.RabbitMQConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private OrderEventPublisher publisher;

    @Test
    void shouldPublishOrderCreatedWithCorrectParameters() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID(), UUID.randomUUID(), List.of(), BigDecimal.TEN);

        publisher.publishOrderCreated(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CREATED_KEY,
                event);
    }

    @Test
    void shouldPublishOrderCancelledWithCorrectParameters() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                UUID.randomUUID(), UUID.randomUUID(), "Cancelled by customer");

        publisher.publishOrderCancelled(event);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.ORDER_EXCHANGE,
                RabbitMQConfig.ORDER_CANCELLED_KEY,
                event);
    }
}
