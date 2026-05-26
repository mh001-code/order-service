# order-service

<p align="center">
  <img alt="Java" src="https://img.shields.io/badge/Java-17-007396?logo=openjdk&logoColor=white">
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white">
  <img alt="PostgreSQL" src="https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white">
  <img alt="RabbitMQ" src="https://img.shields.io/badge/RabbitMQ-3-FF6600?logo=rabbitmq&logoColor=white">
  <img alt="Docker" src="https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white">
  <img alt="Swagger" src="https://img.shields.io/badge/Swagger-UI-85EA2D?logo=swagger&logoColor=black">
  <img alt="Prometheus" src="https://img.shields.io/badge/Prometheus-metrics-E6522C?logo=prometheus&logoColor=white">
  <img alt="Zipkin" src="https://img.shields.io/badge/Zipkin-tracing-FE7139">
  <img alt="Grafana" src="https://img.shields.io/badge/Grafana-dashboard-F46800?logo=grafana&logoColor=white">
  <img alt="k6" src="https://img.shields.io/badge/k6-load_test-7D64FF">
  <img alt="CI" src="https://github.com/mh001-code/order-service/actions/workflows/ci.yml/badge.svg">
</p>

Entry point of the [Order Processing System](https://github.com/mh001-code) — a microservices portfolio project demonstrating asynchronous messaging, distributed systems, and clean hexagonal architecture.

---

## Table of Contents

- [About](#about)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Business Rules](#business-rules)
- [Running Locally](#running-locally)
- [Endpoints](#endpoints)
- [Observability](#observability)
- [Technical Decisions](#technical-decisions)

---

## About

The `order-service` is the entry point of the system. It receives customer orders via REST, applies business rules, persists them in PostgreSQL, and publishes domain events to RabbitMQ so downstream services can react independently — without any direct coupling.

```
Client → POST /orders → order-service → PostgreSQL
                                      → RabbitMQ (order.created / order.cancelled)
                                                  ↓                  ↓
                                        inventory-service   notification-service
```

---

## Architecture

The project follows **Hexagonal Architecture (Ports & Adapters)**, keeping the domain isolated from infrastructure concerns.

```
┌─────────────────────────────────────────────────────┐
│                     API Layer                        │
│           Controllers · DTOs · ExceptionHandler      │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                 Application Layer                    │
│            Use Cases · Port Interfaces               │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│                  Domain Layer                        │
│       Order · OrderItem · OrderStatus · Exceptions   │
└─────────────────────────────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              Infrastructure Layer                    │
│       JPA Adapter · RabbitMQ Publisher · Config      │
└─────────────────────────────────────────────────────┘
```

```mermaid
graph TD
    Client -->|HTTP| OrderController
    OrderController --> CreateOrderUseCase
    OrderController --> CancelOrderUseCase
    CreateOrderUseCase -->|port/out| JpaAdapter
    CreateOrderUseCase -->|port/out| EventPublisher
    CancelOrderUseCase -->|port/out| JpaAdapter
    CancelOrderUseCase -->|port/out| EventPublisher
    JpaAdapter -->|JPA| PostgreSQL
    EventPublisher -->|AMQP| RabbitMQ
    RabbitMQ -->|order.created / order.cancelled| DownstreamServices
```

**Base package:** `com.orderprocessing.order.service`

```
com.orderprocessing.order.service
├── domain
│   ├── model          # Order, OrderItem, OrderStatus
│   └── exception      # OrderNotFoundException, OrderAlreadyCancelledException, InvalidOrderException
├── application
│   ├── usecase        # CreateOrderService, CancelOrderService
│   └── port
│       ├── in         # CreateOrderUseCase, CancelOrderUseCase
│       └── out        # OrderRepositoryPort, OrderEventPublisherPort
├── infrastructure
│   ├── persistence    # JPA repositories + adapters
│   ├── messaging      # RabbitMQ publisher, event records
│   └── config         # RabbitMQConfig
└── api
    ├── controller     # OrderController, HealthController
    ├── dto            # Request/Response records
    └── handler        # GlobalExceptionHandler
```

---

## Tech Stack

| Technology | Version | Role |
|---|---|---|
| [Java](https://openjdk.org/) | 17 | Primary language |
| [Spring Boot](https://spring.io/projects/spring-boot) | 3.5 | Web framework + DI |
| [Spring AMQP](https://spring.io/projects/spring-amqp) | — | RabbitMQ integration |
| [Spring Data JPA](https://spring.io/projects/spring-data-jpa) | — | ORM persistence |
| [PostgreSQL](https://www.postgresql.org/) | 16 | Relational database |
| [Flyway](https://flywaydb.org/) | — | Database migrations |
| [Lombok](https://projectlombok.org/) | — | Boilerplate reduction |
| [JUnit 5 + Mockito](https://junit.org/junit5/) | — | Unit testing |
| [Testcontainers](https://testcontainers.com/) | — | Integration tests with real PostgreSQL + RabbitMQ |
| [Docker](https://www.docker.com/) | — | Containerization (multi-stage build) |
| [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) | — | Health checks + metrics endpoints |
| [Micrometer + Prometheus](https://micrometer.io/) | — | Business metrics (orders criados, cancelados, latência) |
| [Micrometer Tracing + Zipkin](https://micrometer.io/docs/tracing) | — | Distributed tracing com 100% sampling |
| [springdoc-openapi](https://springdoc.org/) | 2.8 | Swagger UI em `/swagger-ui.html` |
| [k6](https://k6.io/) | — | Load test com thresholds: p95 < 500ms, error < 1% |
| [Railway](https://railway.app/) | — | Production hosting |
| [GitHub Actions](https://github.com/features/actions) | — | CI/CD pipeline |

---

## Business Rules

- `totalAmount` must be **≥ R$1.00** — otherwise `422 Unprocessable Entity`
- Item `quantity` must be between **1 and 100**
- Order status flow: `PENDING` → `CONFIRMED` → `CANCELLED`
- Cancelling an already-cancelled order returns `409 Conflict`
- After confirmation, `order.created` is published to RabbitMQ
- After cancellation, `order.cancelled` is published to RabbitMQ

### Events published

| Event | Routing Key | Payload |
|---|---|---|
| `OrderCreatedEvent` | `order.created` | `orderId`, `customerId`, `items`, `totalAmount` |
| `OrderCancelledEvent` | `order.cancelled` | `orderId`, `customerId`, `reason` |

Dead Letter Queues (`order.created.dlq`, `order.cancelled.dlq`) capture messages that fail processing in downstream services.

---

## Running Locally

### Prerequisites

- Java 17+
- Docker and Docker Compose
- Maven (or use the included `./mvnw` wrapper)

### 1. Clone the repository

```bash
git clone https://github.com/mh001-code/order-service.git
cd order-service
```

### 2. Start the full system (recommended)

This repo includes a `docker-compose.yml` that runs all 3 services + infrastructure + observability stack:

```bash
docker-compose up -d
```

| Service | URL |
|---|---|
| order-service API | http://localhost:8085 |
| inventory-service API | http://localhost:8081 |
| notification-service API | http://localhost:8082 |
| Swagger UI (order) | http://localhost:8085/swagger-ui.html |
| Swagger UI (inventory) | http://localhost:8081/swagger-ui.html |
| Swagger UI (notification) | http://localhost:8082/swagger-ui.html |
| RabbitMQ Management | http://localhost:15672 (guest/guest) |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Zipkin | http://localhost:9411 |

### 3. Run only this service locally

```bash
# Start only PostgreSQL and RabbitMQ
docker-compose up -d postgres-orders rabbitmq

./mvnw spring-boot:run
```

### 4. Run the tests

```bash
# All tests including integration (Docker required for Testcontainers)
./mvnw test
```

### 5. Run the load test (k6)

```bash
# Prerequisites: k6 installed (https://k6.io/docs/getting-started/installation)
# Requires all services running via docker-compose

k6 run k6/load-test.js
```

The script ramps up to 25 virtual users over 2.5 minutes, creates orders across 5 products, cancels ~30% of them, and reports p50/p95 latency, throughput and error rate. Thresholds: **p95 < 500ms** and **error rate < 1%**.

---

## Endpoints

| Method | Route | Description | Status |
|---|---|---|---|
| `POST` | `/orders` | Create a new order | 201 |
| `GET` | `/orders/{id}` | Get order by ID | 200 / 404 |
| `GET` | `/orders?customerId={id}` | List orders for a customer | 200 |
| `PATCH` | `/orders/{id}/cancel` | Cancel an order | 200 / 404 / 409 |
| `GET` | `/health` | Health check | 200 |

### Create Order — Example

```json
POST /orders
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "items": [
    {
      "productId": "a3bb189e-8bf9-3888-9912-ace4e6543002",
      "productName": "Notebook",
      "quantity": 1,
      "unitPrice": 3500.00
    }
  ]
}
```

### HTTP Status Codes

| Status | Situation |
|---|---|
| `201 Created` | Order successfully created |
| `200 OK` | Query or cancellation successful |
| `404 Not Found` | Order not found |
| `409 Conflict` | Order already cancelled |
| `422 Unprocessable Entity` | Total below R$1.00 or invalid quantity |

---

## Observability

| Endpoint | Descrição |
|---|---|
| `GET /actuator/health` | Status da aplicação + dependências |
| `GET /actuator/metrics` | Listagem de métricas disponíveis |
| `GET /actuator/prometheus` | Métricas em formato Prometheus |
| `GET /swagger-ui.html` | Documentação interativa da API |

**Métricas de negócio expostas:**
- `orders_created_total{status="success|invalid"}` — contador de pedidos criados
- `orders_cancelled_total` — contador de pedidos cancelados
- `orders_create_duration_seconds` — histograma de latência de criação (p50/p95 visível no Grafana)

**Distributed tracing:** cada request recebe um `traceId` que propaga via RabbitMQ headers para `inventory-service` e `notification-service`. O trace completo é visualizável no Zipkin em `http://localhost:9411`.

---

## Technical Decisions

**RabbitMQ over direct REST calls**
The `order-service` has zero knowledge of `inventory-service` or `notification-service`. It only publishes events. This means downstream services can be added, removed, or restarted without any change here — and if a consumer is temporarily down, messages queue up safely instead of failing.

**Dead Letter Queues**
Messages that fail processing after exhausting retries are routed to DLQs (`order.created.dlq`, `order.cancelled.dlq`). This prevents data loss and gives operators visibility into processing failures without blocking the main queues.

**Hexagonal Architecture**
Use cases depend on port interfaces, not concrete implementations. `CreateOrderService` calls `OrderRepositoryPort` and `OrderEventPublisherPort` — it has no idea whether the backing store is PostgreSQL or the broker is RabbitMQ. This makes unit testing straightforward with pure Mockito mocks.

**Testcontainers for integration tests**
Tests that mock the database can pass even when real SQL behavior would fail. Testcontainers spins up a real PostgreSQL and RabbitMQ instance per test run, ensuring migrations, constraints, and event publishing behave exactly as in production.

---

<p align="center">
  Built by <a href="mailto:marcioincode@gmail.com">Márcio Henrique</a>
</p>
