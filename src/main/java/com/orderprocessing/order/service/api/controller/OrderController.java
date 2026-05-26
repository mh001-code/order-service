package com.orderprocessing.order.service.api.controller;

import com.orderprocessing.order.service.api.dto.CreateOrderRequest;
import com.orderprocessing.order.service.api.dto.OrderResponse;
import com.orderprocessing.order.service.application.port.in.CancelOrderUseCase;
import com.orderprocessing.order.service.application.port.in.CreateOrderUseCase;
import com.orderprocessing.order.service.application.port.in.OrderItemInput;
import com.orderprocessing.order.service.application.port.out.OrderPersistencePort;
import com.orderprocessing.order.service.domain.exception.OrderNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Orders", description = "Gerenciamento de pedidos")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CreateOrderUseCase createOrderUseCase;
    private final CancelOrderUseCase cancelOrderUseCase;
    private final OrderPersistencePort orderPersistencePort;

    @Operation(summary = "Criar pedido", description = "Cria um novo pedido e publica order.created no RabbitMQ")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pedido criado com sucesso"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "422", description = "Total do pedido abaixo de R$1,00")
    })
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody @Valid CreateOrderRequest request) {
        List<OrderItemInput> items = request.items().stream()
                .map(i -> new OrderItemInput(i.productId(), i.productName(), i.quantity(), i.unitPrice()))
                .toList();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponse.from(createOrderUseCase.execute(request.customerId(), items)));
    }

    @Operation(summary = "Buscar pedido por ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido encontrado"),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado")
    })
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return orderPersistencePort.findById(id)
                .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    @Operation(summary = "Listar pedidos por cliente")
    @ApiResponse(responseCode = "200", description = "Lista de pedidos do cliente (pode ser vazia)")
    @GetMapping
    public ResponseEntity<List<OrderResponse>> getOrdersByCustomer(@RequestParam UUID customerId) {
        List<OrderResponse> orders = orderPersistencePort.findByCustomerId(customerId).stream()
                .map(OrderResponse::from)
                .toList();
        return ResponseEntity.ok(orders);
    }

    @Operation(summary = "Cancelar pedido", description = "Cancela o pedido e publica order.cancelled no RabbitMQ")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pedido cancelado com sucesso"),
        @ApiResponse(responseCode = "404", description = "Pedido não encontrado"),
        @ApiResponse(responseCode = "409", description = "Pedido já estava cancelado")
    })
    @PatchMapping("/{id}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable UUID id) {
        return ResponseEntity.ok(OrderResponse.from(cancelOrderUseCase.execute(id)));
    }
}
