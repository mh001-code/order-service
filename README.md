# order-service

[![CI](https://github.com/mh001-code/order-service/actions/workflows/ci.yml/badge.svg)](https://github.com/mh001-code/order-service/actions/workflows/ci.yml)

Core service of the [Order Processing System](https://github.com/mh001-code) — a microservices portfolio project demonstrating asynchronous messaging, distributed systems, and clean architecture.

## Overview

The `order-service` is the entry point of the system. It receives customer orders, applies business rules, persists them in PostgreSQL, and publishes domain events to RabbitMQ so downstream services can react independently.

```
Client → POST /orders → order-service → order.created   → RabbitMQ
                                      → order.cancelled  →
```

## Architecture

Hexagonal (ports-and-adapters) architecture:

```
com.orderprocessing.order.service
├── domain/         # Entities, enums, domain exceptions
├── application/    # Use cases (port/in), repository & messaging interfaces (port/out), domain events
├── infrastructure/ # JPA adapter, RabbitMQ publisher, Spring config
└── api/            # REST controllers, DTOs, global exception handler
```

The `order-service` **does not know** that `inventory-service` or `notification-service` exist. It only publishes events — coupling is zero.

## Tech Stack

| Technology | Role |
|---|---|
| Java 17 | Language |
| Spring Boot 3.5 | Framework |
| Spring AMQP | RabbitMQ integration |
| PostgreSQL 16 | Persistence |
| Flyway | DB migrations |
| JUnit 5 + Mockito | Unit tests |
| Testcontainers | Integration tests |
| Docker | Containerization |
| GitHub Actions | CI/CD |

## Endpoints

| Method | Route | Description | Status |
|---|---|---|---|
| `POST` | `/orders` | Create a new order | 201 |
| `GET` | `/orders/{id}` | Get order by ID | 200 / 404 |
| `GET` | `/orders?customerId={id}` | List orders for a customer | 200 |
| `PATCH` | `/orders/{id}/cancel` | Cancel an order | 200 / 404 / 409 |
| `GET` | `/health` | Health check | 200 |

### Create Order — Example

```bash
curl -X POST http://localhost:8080/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "550e8400-e29b-41d4-a716-446655440000",
    "items": [
      {
        "productId": "a3bb189e-8bf9-3888-9912-ace4e6543002",
        "productName": "Notebook",
        "quantity": 1,
        "unitPrice": 3500.00
      }
    ]
  }'
```

## Business Rules

- `totalAmount` must be **>= R$1.00** — else `422 Unprocessable Entity`
- Item `quantity` must be between **1 and 100**
- Cancelling an already-cancelled order returns `409 Conflict`
- Order status flow: `PENDING` -> `CONFIRMED` -> `CANCELLED`

## RabbitMQ Events

| Event | Routing Key | Payload |
|---|---|---|
| Order confirmed | `order.created` | `orderId`, `customerId`, `items`, `totalAmount` |
| Order cancelled | `order.cancelled` | `orderId`, `customerId`, `reason` |

Dead Letter Queues (`order.created.dlq`, `order.cancelled.dlq`) capture failed messages for analysis.

## Running Locally

**Prerequisites:** Java 17, Maven, Docker

```bash
# Start PostgreSQL and RabbitMQ
docker-compose up -d

# Run the service
./mvnw spring-boot:run
```

RabbitMQ Management UI: http://localhost:15672 (guest / guest)

## Running Tests

```bash
# Unit tests only (no Docker required)
./mvnw test -Dtest="CreateOrderServiceTest,CancelOrderServiceTest,OrderEventPublisherTest"

# All tests including integration (Docker required)
./mvnw test
```

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/orders` | DB connection |
| `SPRING_DATASOURCE_USERNAME` | `orders_user` | DB user |
| `SPRING_DATASOURCE_PASSWORD` | `orders_pass` | DB password |
| `SPRING_RABBITMQ_HOST` | `localhost` | RabbitMQ host |
| `SPRING_RABBITMQ_PORT` | `5672` | RabbitMQ port |
| `PORT` | `8080` | Server port |

## Related Services

- [inventory-service](https://github.com/mh001-code/inventory-service) — consumes `order.created` / `order.cancelled`, manages stock
- [notification-service](https://github.com/mh001-code/notification-service) — consumes events, records and simulates notifications
