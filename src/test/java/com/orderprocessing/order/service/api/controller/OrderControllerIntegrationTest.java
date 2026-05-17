package com.orderprocessing.order.service.api.controller;

import com.orderprocessing.order.service.api.dto.CreateOrderItemRequest;
import com.orderprocessing.order.service.api.dto.CreateOrderRequest;
import com.orderprocessing.order.service.api.dto.OrderResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class OrderControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    @ServiceConnection
    static RabbitMQContainer rabbit = new RabbitMQContainer("rabbitmq:3.13-management");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void shouldCreateOrderAndReturn201() {
        ResponseEntity<OrderResponse> response = restTemplate.postForEntity(
                "/orders", validRequest(), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo("CONFIRMED");
        assertThat(response.getBody().totalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void shouldReturnOrderByIdAfterCreation() {
        OrderResponse created = restTemplate.postForObject("/orders", validRequest(), OrderResponse.class);

        ResponseEntity<OrderResponse> response = restTemplate.getForEntity(
                "/orders/" + created.id(), OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().id()).isEqualTo(created.id());
        assertThat(response.getBody().customerId()).isEqualTo(created.customerId());
    }

    @Test
    void shouldReturnOrdersByCustomerId() {
        CreateOrderRequest request = validRequest();
        OrderResponse created = restTemplate.postForObject("/orders", request, OrderResponse.class);

        ResponseEntity<List> response = restTemplate.getForEntity(
                "/orders?customerId=" + created.customerId(), List.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotEmpty();
    }

    @Test
    void shouldCancelOrderSuccessfully() {
        OrderResponse created = restTemplate.postForObject("/orders", validRequest(), OrderResponse.class);

        ResponseEntity<OrderResponse> response = restTemplate.exchange(
                "/orders/" + created.id() + "/cancel",
                HttpMethod.PATCH, null, OrderResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo("CANCELLED");
    }

    @Test
    void shouldReturn404WhenOrderNotFound() {
        ResponseEntity<String> response = restTemplate.getForEntity(
                "/orders/" + UUID.randomUUID(), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void shouldReturn409WhenCancellingAlreadyCancelledOrder() {
        OrderResponse created = restTemplate.postForObject("/orders", validRequest(), OrderResponse.class);
        restTemplate.exchange("/orders/" + created.id() + "/cancel",
                HttpMethod.PATCH, null, OrderResponse.class);

        ResponseEntity<String> response = restTemplate.exchange(
                "/orders/" + created.id() + "/cancel",
                HttpMethod.PATCH, null, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void shouldReturn400WhenPayloadIsInvalid() {
        CreateOrderRequest invalid = new CreateOrderRequest(null, List.of());

        ResponseEntity<String> response = restTemplate.postForEntity("/orders", invalid, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private CreateOrderRequest validRequest() {
        return new CreateOrderRequest(
                UUID.randomUUID(),
                List.of(new CreateOrderItemRequest(
                        UUID.randomUUID(), "Product A", 2, new BigDecimal("10.00"))));
    }
}
