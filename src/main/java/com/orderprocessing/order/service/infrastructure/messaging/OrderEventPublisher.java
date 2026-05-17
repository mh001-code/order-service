package com.orderprocessing.order.service.infrastructure.messaging;

import com.orderprocessing.order.service.application.event.OrderCancelledEvent;
import com.orderprocessing.order.service.application.event.OrderCreatedEvent;
import com.orderprocessing.order.service.application.port.out.OrderEventPublisherPort;
import com.orderprocessing.order.service.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher implements OrderEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CREATED_KEY, event);
            log.info("Published order.created for orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to publish order.created for orderId={}", event.orderId(), e);
        }
    }

    @Override
    public void publishOrderCancelled(OrderCancelledEvent event) {
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, RabbitMQConfig.ORDER_CANCELLED_KEY, event);
            log.info("Published order.cancelled for orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("Failed to publish order.cancelled for orderId={}", event.orderId(), e);
        }
    }
}
