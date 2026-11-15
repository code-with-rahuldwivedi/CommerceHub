# ShopSphere – E-Commerce Microservices Backend

A Java-based e-commerce backend application built using **Spring Boot 3 and Microservices Architecture**. ShopSphere demonstrates backend development concepts including secure authentication, product management, order processing, inventory coordination, and event-driven communication using Apache Kafka.

The project focuses on building maintainable backend services using REST APIs, JWT authentication, service discovery, database migrations, idempotent operations, and asynchronous event processing.

## Tech Stack

| Category | Technologies |
|---|---|
| Language | Java 17 |
| Backend Framework | Spring Boot 3, Spring MVC |
| Security | Spring Security, JWT, BCrypt |
| Database | MySQL 8 |
| ORM | Spring Data JPA, Hibernate |
| Database Migration | Flyway |
| Microservices | Spring Cloud Gateway, Eureka |
| Service Communication | OpenFeign, REST APIs |
| Messaging | Apache Kafka |
| Resilience | Resilience4j |
| Build Tool | Maven |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Containerization | Docker, Docker Compose |
| Monitoring | Prometheus configuration |

## Architecture

```text
                         Client
                           |
                           v
                   API Gateway :8080
                           |
          +----------------+----------------+
          |                |                |
          v                v                v
     User Service     Product Service   Order Service
       :8081             :8082             :8083
                                              |
                                              |
                                         Kafka Events
                                              |
                                              v
                                       Inventory Service
                                            :8084

                   Eureka Service Registry
                           :8761
```

### Architecture Overview

- **API Gateway:** Provides a single entry point for incoming client requests and routes them to the appropriate microservice.
- **Eureka Server:** Supports service registration and discovery.
- **OpenFeign:** Enables synchronous communication between microservices.
- **Apache Kafka:** Supports asynchronous communication between Order Service and Inventory Service.
- **MySQL:** Each business service maintains its own database responsibilities.
- **Outbox Pattern:** Helps coordinate database changes and event publishing.

## Project Structure

```text
ShopSphere-Microservices/
│
├── service-registry/
├── cloud-gateway/
├── user-service/
├── product-service/
├── order-service/
├── inventory-service/
│
├── prometheus/
├── docker-compose.yml
└── README.md
```

Each microservice has its own Maven configuration and application properties. Database schema changes are maintained using Flyway migrations.

## Microservices

### 1. Service Registry – Eureka

**Port:** `8761`

- Service registration and discovery.
- Centralized service registry.
- Dynamic discovery of registered services.

### 2. API Gateway

**Port:** `8080`

- Central entry point for client requests.
- Routes requests to appropriate microservices.
- JWT authentication filter.
- Role-based access control.
- Resilience4j circuit-breaker integration.
- Fallback and timeout configuration.

### 3. User Service

**Port:** `8081`

- User registration and login.
- Password encryption using BCrypt.
- JWT generation and validation.
- Role-based authorization.
- Spring Security integration.
- MySQL persistence.

**Supported roles:**
- `USER`
- `ADMIN`

### 4. Product Service

**Port:** `8082`

- Create and manage products.
- Retrieve all products.
- Retrieve product details by ID.
- Request validation.
- JWT verification and authorization.
- MySQL persistence.

| Operation | Access |
|---|---|
| Get all products | Public, as configured |
| Get product by ID | Public, as configured |
| Create product | ADMIN |
| Update product | ADMIN |
| Delete product | ADMIN |

### 5. Order Service

**Port:** `8083`

- Place and persist customer orders.
- Retrieve product details using OpenFeign.
- Calculate order totals using product prices and quantities.
- Manage order statuses.
- Validate authenticated user identity.
- Handle duplicate requests using idempotency keys.
- Prevent duplicate order creation.
- Publish order events using the Outbox Pattern.
- Consume inventory result events from Kafka.

**Idempotency handling:**

The Order Service uses an `Idempotency-Key` request header to prevent duplicate order creation.

- Same key + same request: Returns the existing order.
- Same key + different request: Returns HTTP `409 Conflict`.
- Database uniqueness constraints help protect against concurrent duplicate requests.

Example:

```http
POST /api/orders
Idempotency-Key: order-shop-001
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### 6. Inventory Service

**Port:** `8084`

- Manages inventory and stock operations.
- Consumes order-related Kafka events.
- Publishes inventory result events.
- Maintains outgoing events using the Outbox Pattern.
- Processes pending outbox events.
- Includes duplicate-operation protection.

Inventory retry behavior, concurrency handling, and recovery guarantees should be validated through implementation and integration tests before being considered production-ready.

## Communication Between Services

ShopSphere uses both synchronous and asynchronous communication.

### Synchronous Communication – OpenFeign

Order Service communicates with Product Service to retrieve product details.

```text
Client
  |
  v
API Gateway
  |
  v
Order Service
  |
  v
OpenFeign
  |
  v
Product Service
```

This allows the Order Service to retrieve product information before processing an order.

### Asynchronous Communication – Apache Kafka

Kafka supports communication between Order Service and Inventory Service.

```text
Order Service
     |
     v
Order Database + Outbox
     |
     v
Outbox Publisher
     |
     v
Kafka Order Event
     |
     v
Inventory Service
     |
     v
Inventory Database + Outbox
     |
     v
Kafka Inventory Result Event
     |
     v
Order Service Consumer
```

### Outbox Pattern

The Outbox Pattern is used to coordinate database updates with event publishing.

Instead of relying on a database update and Kafka publishing as two independent operations, an event is stored in the database as part of the relevant transaction and published asynchronously.

This reduces the risk of losing events when a database transaction succeeds but event publishing fails.

Reliable operation still depends on retry handling, event claiming, transaction boundaries, and idempotent consumers.

## Security

- JWT-based authentication.
- Stateless Spring Security configuration.
- BCrypt password hashing.
- Role-based authorization.
- Token validation at the gateway and relevant services.
- Environment-based configuration for sensitive information.

### Environment Variables

Configure sensitive values through environment variables.

```properties
DB_PASSWORD=
JWT_SECRET=
INVENTORY_SERVICE_TOKEN=
```

Never commit actual passwords, JWT secrets, access tokens, or production credentials to GitHub.

## Reliability and Error Handling

The project includes reliability-oriented mechanisms such as:

- Idempotency-key handling for order requests.
- Database uniqueness constraints.
- Outbox-based event publishing.
- Kafka event consumers.
- Resilience4j circuit-breaker configuration.
- Configurable timeout and fallback handling.
- Flyway database migrations.
- Unit and service-level tests.

These mechanisms improve reliability but do not automatically guarantee exactly-once processing or eliminate all distributed-system failures.

## Database Design

Each microservice maintains data according to its business responsibility.

| Service | Primary Data |
|---|---|
| User Service | Users, authentication, roles |
| Product Service | Products and categories |
| Order Service | Orders, order statuses, outbox events |
| Inventory Service | Inventory records, outbox events |

**Database technologies:**
- MySQL 8
- Spring Data JPA
- Hibernate
- Flyway

## REST API Overview

### Authentication APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Authenticate a user |

### Product APIs

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/getAllProducts` | Retrieve all products |
| GET | `/api/product/{id}` | Retrieve product by ID |
| POST | `/api/createProduct` | Create a product |
| PUT | `/api/product/{id}` | Update a product |
| DELETE | `/api/product/{id}` | Delete a product |

### Order APIs

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/orders` | Place an order |

Order creation requires authentication and an `Idempotency-Key` header. Refer to the service controllers for the exact request and response formats.

## Testing

The project contains tests for service logic, order processing, and Kafka event handling.

Run tests from an individual microservice directory:

```bash
mvn clean verify
```

Run the command separately in:

```text
service-registry
cloud-gateway
user-service
product-service
order-service
inventory-service
```

A successful build verifies the checks configured in that module. Additional integration, security, concurrency, and failure-recovery tests are recommended before production deployment.

## Docker and Deployment

Dockerfiles and Docker Compose configuration are included in the repository.

Validate the Docker Compose configuration:

```bash
docker compose config
```

Before starting the complete application, configure the required environment variables and verify that the Compose configuration includes all required services, databases, Kafka, health checks, and dependencies.

Prometheus configuration is also included for monitoring-related setup.

## Local Development Setup

### Prerequisites

- JDK 17
- Maven
- MySQL 8
- Apache Kafka
- Git
- Docker (optional)

### Starting the Services

Start each service in a separate terminal.

**1. Eureka Service Registry**

```bash
cd service-registry
mvn spring-boot:run
```

**2. User Service**

```bash
cd user-service
mvn spring-boot:run
```

**3. Product Service**

```bash
cd product-service
mvn spring-boot:run
```

**4. Inventory Service**

```bash
cd inventory-service
mvn spring-boot:run
```

**5. Order Service**

```bash
cd order-service
mvn spring-boot:run
```

**6. API Gateway**

```bash
cd cloud-gateway
mvn spring-boot:run
```

Ensure that MySQL, Kafka, environment variables, and service configurations are ready before starting the application.

## Project Status

| Component | Status |
|---|---|
| Eureka Service Registry | Implemented |
| API Gateway and Routing | Implemented |
| User Authentication and JWT | Implemented |
| Product Management APIs | Implemented |
| Order Creation and Persistence | Implemented |
| OpenFeign Integration | Implemented |
| Order Idempotency Handling | Tested |
| Kafka Event Processing Components | Implemented |
| Outbox Pattern Components | Implemented |
| Inventory Service Components | Implemented |
| Flyway Migration Files | Included |
| Docker and Compose Configuration | Included |
| Full Production Deployment Validation | Pending |

*Note: Implemented components and configurations should not be confused with complete production validation. Integration, concurrency, recovery, and deployment tests are still important.*

## Future Enhancements

- Payment Service integration.
- Email and push notifications.
- Kubernetes deployment.
- CI/CD pipeline.
- Centralized logging and distributed tracing.
- Automated load and performance testing.
- Production-grade secrets management.
- Monitoring dashboards and alerting.
- Backup and disaster recovery.
- Deployment rollback procedures.

## Developer

**Rahul Dwivedi**

Java Backend / Full Stack Developer

**Core Skills:** Java, Spring Boot, Spring Security, Microservices, REST APIs, MySQL, Hibernate/JPA, JWT, Apache Kafka, Docker, AWS.

---

If you find this project useful, consider giving the repository a ⭐ star.
