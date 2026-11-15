# ShopSphere Order Service

The **Order Service** is a Spring Boot microservice in the ShopSphere e-commerce backend. It manages customer orders, order status transitions, order history, cancellation, and communication with catalog, inventory, and payment services.

It exposes REST APIs, validates access tokens issued by the User Service, persists order data in MySQL, and uses Kafka messaging with an Outbox/Inbox design for reliable asynchronous workflows.

## Table of Contents

- [Features](#features)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)
- [Order Lifecycle](#order-lifecycle)
- [REST API](#rest-api)
- [Authentication and Authorization](#authentication-and-authorization)
- [Kafka and Event-Driven Communication](#kafka-and-event-driven-communication)
- [Reliability and Resilience](#reliability-and-resilience)
- [Database and Migrations](#database-and-migrations)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Run Locally](#run-locally)
- [Run with Docker Compose](#run-with-docker-compose)
- [API Documentation and Monitoring](#api-documentation-and-monitoring)
- [Testing](#testing)
- [Project Structure](#project-structure)

## Features

- Place orders using either the single-item request format or an `items` collection.
- Derive the customer identity from the authenticated JWT rather than trusting a customer ID supplied in the request body.
- Retrieve an individual order, list the current customer's orders, and view order status history.
- Support order cancellation and administrative shipping/delivery operations.
- Use an `Idempotency-Key` to make order creation safe to retry.
- Validate product information through the Product Service.
- Publish order events through Kafka.
- Persist outgoing events in a transactional Outbox and track processed incoming events with an Inbox.
- Consume inventory and payment result events when inbound messaging is enabled.
- Apply retry and circuit-breaker protection to catalog communication.
- Validate database schema using Flyway migrations and Hibernate schema validation.
- Expose health, readiness, info, and Prometheus metrics endpoints.

## Technology Stack

| Area | Technologies |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.5.16 |
| Build | Maven |
| API | Spring Web, Bean Validation |
| Persistence | Spring Data JPA, Hibernate |
| Database | MySQL 8.x |
| Schema migration | Flyway |
| Security | Spring Security, OAuth2 Resource Server, JWT (JWKS) |
| Service communication | Spring Cloud OpenFeign, Eureka Client |
| Resilience | Resilience4j |
| Messaging | Apache Kafka, Spring Kafka |
| API documentation | Springdoc OpenAPI / Swagger UI |
| Monitoring | Spring Boot Actuator, Micrometer, Prometheus |
| Distributed tracing | Micrometer Tracing, OpenTelemetry |
| Testing | JUnit 5, Spring Boot Test, H2, Testcontainers, Kafka Test |
| Containerization | Docker, Docker Compose |

## Architecture

```text
                         Client
                           |
                           | Bearer access token
                           v
                    API Gateway (optional)
                           |
                           v
                  +-------------------+
                  |   Order Service   |
                  |    Port 8083      |
                  +-------------------+
                    |       |       |
           REST/Feign|       |Kafka  |JWT validation
                    v       v       v
              Product     Kafka   User Service
              Service     Broker  (JWKS endpoint)
                    |
                    v
               MySQL
          (order database)

       Kafka event consumers:
       Inventory Service / Payment Service
```

The service can discover the Product Service through Eureka when `PRODUCT_SERVICE_URL` is not configured. For local or container-based setups, a direct Product Service URL can be supplied.

## Order Lifecycle

The service defines these order statuses:

| Status | Meaning |
|---|---|
| `PENDING` | Order has been created and is awaiting the next workflow result. |
| `AWAITING_PAYMENT` | Inventory/workflow has progressed and payment is pending. |
| `CONFIRMED` | Order has been confirmed. |
| `FAILED` | Order processing failed. |
| `SHIPPED` | Order has been marked as shipped. |
| `DELIVERED` | Order has been delivered. |
| `CANCEL_REQUESTED` | A cancellation has been requested and may require downstream processing. |
| `CANCEL_FAILED` | Cancellation processing failed. |
| `CANCELLED` | Order has been cancelled. |

The available transitions are controlled by the order state machine and business services; clients should not update status fields directly.

Payment processing is configurable. With `ORDER_PAYMENT_ENABLED=true`, the workflow can use the `PENDING -> AWAITING_PAYMENT -> CONFIRMED` path. Expiry processing for pending/payment-waiting orders can be enabled separately.

## REST API

Base path: `/api`

All order endpoints require a valid Bearer access token.

### Customer APIs

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/orders` | Place an order |
| `GET` | `/api/orders` | List the authenticated customer's orders |
| `GET` | `/api/orders/{id}` | Get an order by ID |
| `GET` | `/api/orders/{id}/history` | Get order status history |
| `POST` | `/api/orders/{id}/cancel` | Request order cancellation |

The order list supports optional `status`, `page`, and `size` query parameters. Defaults are `page=0` and `size=20`; the maximum page size is 100.

#### Place an order

`POST /api/orders`

Required header:

```http
Authorization: Bearer <access-token>
Idempotency-Key: <unique-client-generated-key>
Content-Type: application/json
```

The request supports either the legacy single-item fields (`productId` and `quantity`) or an `items` list. It can also include a shipping address and contact email.

Example single-item request:

```json
{
  "productId": 3,
  "quantity": 2,
  "contactEmail": "customer@example.com"
}
```

Example items-based request:

```json
{
  "items": [
    {
      "productId": 3,
      "quantity": 2
    }
  ],
  "contactEmail": "customer@example.com"
}
```

The exact item and address fields are defined by `OrderItemRequest` and `AddressRequest` in the source code.

Responses:
- `201 Created` for a newly created order. The response includes a `Location` header.
- `200 OK` with `Idempotent-Replayed: true` when the same key replays the already-created order.
- `409 Conflict` when the same idempotency key is reused with a different request.

### Administrative APIs

These endpoints require the `ADMIN` role.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/admin/orders` | List orders across customers |
| `GET` | `/api/admin/orders/{id}` | Get an order |
| `POST` | `/api/admin/orders/{id}/ship` | Mark a confirmed order as shipped |
| `POST` | `/api/admin/orders/{id}/deliver` | Mark a shipped order as delivered |

The admin list supports optional `userId`, `status`, `page`, and `size` query parameters.

### Example requests

Get the current customer's orders:

```bash
curl --request GET \
  --url http://localhost:8083/api/orders \
  --header "Authorization: Bearer <access-token>"
```

Place an order:

```bash
curl --request POST \
  --url http://localhost:8083/api/orders \
  --header "Authorization: Bearer <access-token>" \
  --header "Idempotency-Key: order-request-001" \
  --header "Content-Type: application/json" \
  --data '{
    "productId": 3,
    "quantity": 1
  }'
```

Cancel an order:

```bash
curl --request POST \
  --url http://localhost:8083/api/orders/1/cancel \
  --header "Authorization: Bearer <access-token>" \
  --header "Content-Type: application/json" \
  --data '{
    "reason": "Customer requested cancellation"
  }'
```

## Authentication and Authorization

The service acts as an OAuth2 Resource Server.

- Access tokens are issued by the ShopSphere User Service.
- The Order Service verifies JWT signatures using the User Service JWKS endpoint.
- JWT issuer and audience are validated.
- The `role` claim is mapped to Spring Security authorities using the `ROLE_` prefix.
- `USER` and `ADMIN` roles can access customer order APIs.
- Only `ADMIN` can access `/api/admin/**`.
- The customer ID is obtained from the authenticated security context.
- API sessions are stateless.

Swagger UI and OpenAPI endpoints are permitted by the service security configuration. Health is public; info and Prometheus endpoints are restricted to internal network addresses.

## Kafka and Event-Driven Communication

The service publishes order lifecycle events to Kafka.

Default topics:

| Topic | Purpose |
|---|---|
| `order.events.v1` | Versioned order lifecycle events |
| `order-events` | Legacy order command/event stream |

Examples of published event types include:

- `ORDER_PLACED`
- `ORDER_AWAITING_PAYMENT`
- `ORDER_CONFIRMED`
- `ORDER_FAILED`
- `ORDER_CANCEL_REQUESTED`
- `ORDER_CANCELLED`
- `ORDER_CANCEL_FAILED`
- `ORDER_SHIPPED`
- `ORDER_DELIVERED`

The service can consume inbound events from inventory and payment services when inbound processing is enabled. Supported event types include:

- `INVENTORY_RESERVED`
- `INVENTORY_RESERVATION_FAILED`
- `INVENTORY_RELEASED`
- `INVENTORY_RELEASE_FAILED`
- `PAYMENT_SUCCEEDED`
- `PAYMENT_FAILED`

Relevant configuration:
- `ORDER_EVENTS_TOPIC`
- `ORDER_LEGACY_EVENTS_ENABLED`
- `ORDER_LEGACY_TOPIC`
- `ORDER_INBOUND_ENABLED`
- `ORDER_INBOUND_TOPICS`
- `INVENTORY_RESULT_TOPIC`

See the event envelope, handlers, and configuration classes in the source for the exact event payload contracts and processing rules.

### Outbox and Inbox

**Transactional Outbox**
- Business changes and outgoing event records are persisted in the database transaction.
- A scheduled publisher polls pending Outbox records and sends them to Kafka.
- Retry attempts and stale claims are configured.
- Events that exceed the configured maximum attempts can be parked in a dead state.

**Inbox / Idempotent event handling**
- Incoming events are tracked to help prevent duplicate processing.
- Event handlers validate and dispatch supported event types.
- Kafka listener acknowledgement and retry behavior are configured by the service.

This approach helps coordinate database state and asynchronous messaging without relying on a database-plus-Kafka distributed transaction.

## Reliability and Resilience

- **Idempotent order creation:** repeated requests with the same key do not create duplicate orders.
- **Catalog retry:** transient Product Service communication failures can be retried.
- **Circuit breaker:** protects the order workflow when the catalog service is unhealthy.
- **Feign timeouts:** connection and read timeouts are configurable.
- **Kafka producer reliability:** producer acknowledgements are set to `all`, idempotence is enabled, and compression uses LZ4.
- **Graceful shutdown:** the application uses graceful server shutdown.
- **Request-size protection:** the configured maximum request body size defaults to 16 KB.
- **Scheduled jobs:** order expiry and Outbox cleanup/publishing are configurable.

## Database and Migrations

The service uses MySQL and Spring Data JPA.

Default local database name:

```text
shopsphere_order_db
```

Flyway manages schema changes under:

```text
src/main/resources/db/migration/
```

The current migration sequence includes:
- `V1` — initial order schema
- `V2` — Outbox claim columns
- `V3` — cancellation command ID
- `V4` — order items, history, and Inbox tables
- `V5` — order v2 columns and backfill
- `V6` — Outbox routing and ordering

Hibernate uses `ddl-auto=validate`; schema changes should be made through Flyway migrations rather than automatic Hibernate DDL generation. Open Session in View is disabled.

## Prerequisites

For local development, install:

- JDK 17
- Maven 3.9+ (or use the included Maven Wrapper)
- MySQL 8.x
- Apache Kafka
- ShopSphere User Service, for obtaining/verifying JWTs
- Product Service, for catalog validation

Eureka is required only when using service discovery.

## Configuration

The application reads its runtime configuration from environment variables.

| Variable | Purpose | Default / Notes |
|---|---|---|
| `SERVER_PORT` | HTTP port | `8083` |
| `DB_URL` | JDBC connection URL | Required |
| `DB_USERNAME` | Database username | Required |
| `DB_PASSWORD` | Database password | Required |
| `DB_POOL_MAX` | Hikari maximum pool size | `20` |
| `DB_POOL_MIN_IDLE` | Hikari minimum idle connections | `5` |
| `KAFKA_BOOTSTRAP_SERVERS` | Kafka broker address | Required |
| `KAFKA_CONSUMER_GROUP` | Kafka consumer group | `order-service-group` |
| `JWT_JWKS_URI` | User Service JWKS URL | Required |
| `JWT_ISSUER` | Expected JWT issuer | `shopsphere-user-service` |
| `JWT_AUDIENCE` | Expected JWT audience | `shopsphere-api` |
| `EUREKA_ENABLED` | Enable Eureka client | `true` |
| `EUREKA_DEFAULT_ZONE` | Eureka server URL | Required when Eureka is enabled |
| `PRODUCT_SERVICE_URL` | Direct Product Service base URL | Empty means Eureka discovery |
| `ORDER_CURRENCY` | Order currency | `INR` |
| `ORDER_MAX_ITEMS` | Maximum items configured for an order | `1` |
| `ORDER_MAX_QUANTITY_PER_ITEM` | Maximum quantity per item | `100` |
| `ORDER_REQUIRE_SHIPPING_ADDRESS` | Require shipping address | `false` |
| `ORDER_PAYMENT_ENABLED` | Enable payment stage | `false` |
| `ORDER_EXPIRY_ENABLED` | Enable pending-order expiry job | `false` |
| `ORDER_INBOUND_ENABLED` | Enable inbound event processing | `false` |
| `OUTBOX_POLL_MS` | Outbox polling delay | `500` |
| `OUTBOX_MAX_ATTEMPTS` | Maximum Outbox publish attempts | `20` |
| `OUTBOX_RETENTION_DAYS` | Outbox retention period | `7` |
| `TRACING_ENABLED` | Enable distributed tracing | `false` |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | OTLP trace collector endpoint | `http://localhost:4318/v1/traces` |

The application YAML also defines defaults for event topics, retry behavior, expiry timeouts, and cleanup scheduling.

## Run Locally

### 1. Start dependencies

Start MySQL and Kafka, and ensure the User Service is running with its JWKS endpoint available.

Example local JWKS endpoint:

```text
http://localhost:8081/.well-known/jwks.json
```

### 2. Create the database

```sql
CREATE DATABASE shopsphere_order_db;
```

Create a database user and grant it access according to your local MySQL setup.

### 3. Set environment variables

Example for PowerShell:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/shopsphere_order_db"
$env:DB_USERNAME="shopsphere"
$env:DB_PASSWORD="your-local-db-password"

$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"

$env:JWT_JWKS_URI="http://localhost:8081/.well-known/jwks.json"
$env:JWT_ISSUER="shopsphere-user-service"
$env:JWT_AUDIENCE="shopsphere-api"

$env:EUREKA_ENABLED="false"
$env:EUREKA_DEFAULT_ZONE="http://localhost:8761/eureka/"
```

If Eureka is enabled, set `EUREKA_ENABLED=true` and point `EUREKA_DEFAULT_ZONE` to the running Eureka server. If Eureka is disabled, configure `PRODUCT_SERVICE_URL` with the reachable Product Service base URL.

### 4. Run the application

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

Linux/macOS:

```bash
./mvnw spring-boot:run
```

The service listens on:

```text
http://localhost:8083
```

Flyway applies pending migrations during startup.

## Run with Docker Compose

The repository includes a local-development `docker-compose.yaml` and a multi-stage `Dockerfile`.

From the service directory:

```bash
docker compose up --build
```

The Compose configuration starts MySQL and builds/runs the service container. It is configured for local development and expects the User Service to be available on the host at port `8081`; the container uses `host.docker.internal` to reach the host JWKS endpoint.

Before using this setup, review the Compose environment for your machine and ensure Kafka is reachable at the address configured through `KAFKA_BOOTSTRAP_SERVERS`. The checked-in Compose file does not define a Kafka broker, so Kafka must be supplied separately. For non-local environments, replace the sample credentials and configure secrets securely.

To stop the containers:

```bash
docker compose down
```

To remove the local Compose database volume/data as well, inspect the Compose volumes and use `docker compose down -v` only if you intentionally want to delete that data.

## API Documentation and Monitoring

When the application is running:

| Resource | URL |
|---|---|
| Swagger UI | `http://localhost:8083/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8083/v3/api-docs` |
| Health | `http://localhost:8083/actuator/health` |
| Info | `http://localhost:8083/actuator/info` |
| Prometheus metrics | `http://localhost:8083/actuator/prometheus` |

The health endpoint is configured for public access. Info and Prometheus are restricted to internal network addresses by the security configuration. Actuator exposes health, info, and Prometheus only.

## Testing

Run the test suite:

```bash
./mvnw test
```

Windows:

```powershell
.\mvnw.cmd test
```

Run verification and generate the JaCoCo report:

```bash
./mvnw verify
```

Windows:

```powershell
.\mvnw.cmd verify
```

The project includes unit and Spring tests, H2-based test configuration, Kafka test support, and Testcontainers dependencies for integration testing. JaCoCo is configured to generate a report during `verify` and enforce a minimum instruction coverage ratio of 60%.

The HTML coverage report is generated at:

```text
target/site/jacoco/index.html
```

## Project Structure

```text
order-service/
├── src/
│   ├── main/
│   │   ├── java/com/shopsphere/orderservice/
│   │   │   ├── client/          # Product/catalog client and resilience gateway
│   │   │   ├── config/          # Application, Kafka, security and resilience config
│   │   │   ├── consumer/        # Inventory result consumer
│   │   │   ├── controller/      # Customer and admin REST controllers
│   │   │   ├── dto/              # Request/response DTOs and mapper
│   │   │   ├── entity/           # Order, items, status, Outbox and Inbox entities
│   │   │   ├── event/            # Event models
│   │   │   ├── exception/        # Domain exceptions and exception handling
│   │   │   ├── job/               # Expiry and retention jobs
│   │   │   ├── messaging/        # Kafka event handling and Outbox pipeline
│   │   │   ├── repository/       # Persistence repositories
│   │   │   ├── security/         # JWT and current-user support
│   │   │   └── service/          # Order business logic
│   │   └── resources/
│   │       ├── db/migration/     # Flyway SQL migrations
│   │       └── application.yml   # Runtime configuration
│   └── test/                     # Unit, integration and application tests
├── Dockerfile
├── docker-compose.yaml
├── pom.xml
└── README.md
