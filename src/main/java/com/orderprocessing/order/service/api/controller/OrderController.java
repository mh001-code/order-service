package com.orderprocessing.order.service.api.controller;

import com.orderprocessing.order.service.api.dto.CreateOrderRequest;
import com.orderprocessing.order.service.api.dto.OrderResponse;
import com.orderprocessing.order.service.application.port.in.CancelOrderUseCase;
import com.orderprocessing.order.service.application.port.in.CreateOrderUseCase;
import com.orderprocessing.order.service.application.port.in.OrderItemInput;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.OrderNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderPersistencePort orderPersistencePort;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        List<OrderItemInput> items = request.items().stream()
                .map(i -> new OrderItemInput(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(createOrderUseCase.execute(request.customerId(), items)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderPersistencePort.findById(id)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(@RequestParam UUID customerId) {
        List<OrderResponse> orders = orderPersistencePort.findByCustomerId(customerId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(OrderResponse.from(cancelOrderUseCase.execute(id)));
    }
}
