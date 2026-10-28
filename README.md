# 🛒 ShopSphere — E-Commerce Microservices Backend

ShopSphere is a Java-based e-commerce backend built using Spring Boot and a microservices architecture. It demonstrates secure authentication, product and order management, inventory coordination, asynchronous communication with Apache Kafka, and reliable event processing using the Outbox Pattern.

The project focuses on backend engineering practices such as JWT authentication, role-based authorization, idempotent operations, database migrations, automated testing, service discovery, and containerized deployment.

## 🚀 Tech Stack

| Category | Technologies |
|---|---|
| Language | Java 17 |
| Backend | Spring Boot 3, Spring MVC |
| Security | Spring Security, JWT, BCrypt |
| Database | MySQL 8, Spring Data JPA, Hibernate |
| Database migrations | Flyway |
| Microservices | Spring Cloud Gateway, Eureka |
| Service communication | OpenFeign, REST APIs |
| Messaging | Apache Kafka |
| Resilience | Resilience4j |
| Build tool | Maven |
| Testing | JUnit, Mockito, Spring Boot Test |
| Containerization | Docker, Docker Compose |
| Monitoring | Prometheus configuration |

## 🏗️ Architecture

```text
                    Client
                       |
                       v
              API Gateway :8080
                       |
          +------------+-------------+
          |            |             |
          v            v             v
     User Service  Product Service  Order Service
       :8081          :8082           :8083
                                         |
                                         v
                                  Kafka Events
                                         |
                                         v
                                  Inventory Service
                                      :8084

              Eureka Service Registry :8761
```

The API Gateway provides a central entry point for client requests. Eureka supports service discovery, while OpenFeign enables synchronous service-to-service communication. Kafka supports asynchronous communication between order and inventory services.

## 📂 Project Structure

```text
ShopSphere-Microservices/
├── service-registry/
├── cloud-gateway/
├── user-service/
├── product-service/
├── order-service/
├── inventory-service/
├── prometheus/
├── docker-compose.yml
└── README.md
```

Each service has its own Maven configuration and application configuration. Database migration files are maintained within the respective services.

## ✅ Microservices

### 1. Service Registry — Eureka

- Service registration and discovery
- Centralized service registry
- Dynamic discovery of registered services

**Default port:** `8761`

### 2. API Gateway

- Central entry point for API requests
- Microservice routing
- JWT authentication filter
- Role-based access control
- Resilience4j circuit-breaker integration
- Fallback handling and timeout configuration

**Default port:** `8080`

### 3. User Service

- User registration and login
- Password hashing with BCrypt
- JWT generation and validation
- Role-based authorization
- Spring Security integration
- MySQL persistence

**Roles:** `USER`, `ADMIN`

**Default port:** `8081`

### 4. Product Service

- Create and manage products
- Retrieve all products
- Retrieve products by ID
- Product request validation
- JWT verification and authorization
- MySQL persistence

**Default port:** `8082`

| Operation | Access |
|---|---|
| Get all products | Public, according to security configuration |
| Get product by ID | Public, according to security configuration |
| Create product | ADMIN |
| Update product | ADMIN |
| Delete product | ADMIN |

### 5. Order Service

- Place and persist orders
- Retrieve product details through OpenFeign
- Calculate order totals using product prices and quantities
- Manage order statuses
- Validate authenticated user identity
- Handle duplicate order requests using idempotency keys
- Publish order events through an Outbox Pattern
- Process inventory result events from Kafka
- Support reliable order and inventory workflow coordination

**Default port:** `8083`

### 6. Inventory Service

- Manage inventory and stock operations
- Consume order-related Kafka events
- Publish inventory result events
- Persist outgoing events using an Outbox Pattern
- Claim and publish pending outbox events
- Support reliable event processing and duplicate-operation protection

**Default port:** `8084`

> Inventory correctness, retry behavior, and recovery guarantees should be verified against the implementation and integration tests before being described as fully production-ready.

## 🔄 Event-Driven Communication

ShopSphere uses both synchronous and asynchronous communication.

### Synchronous communication

OpenFeign allows the Order Service to retrieve product details from the Product Service.

```text
Client → API Gateway → Order Service
                            |
                            └── OpenFeign → Product Service
```

### Asynchronous communication

Apache Kafka connects the order and inventory workflows.

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

The Outbox Pattern is intended to reduce the risk of database changes succeeding while corresponding events fail to be published. Its reliability depends on transaction boundaries, retry handling, event claiming, and consumer idempotency.

## 🔐 Security

- JWT-based authentication
- Stateless Spring Security configuration
- BCrypt password hashing
- Role-based authorization for protected operations
- Token validation at the gateway and relevant services
- Environment-based configuration for sensitive values

Configure credentials and JWT secrets through environment variables rather than committing real secrets to the repository.

Example environment-variable names:

```text
DB_PASSWORD
JWT_SECRET
INVENTORY_SERVICE_TOKEN
```

Set the values locally or through your deployment environment. Never commit actual passwords, tokens, or production secrets.

## 🛡️ Reliability and Resilience

The project includes production-oriented reliability mechanisms:

- Resilience4j circuit breakers and fallback handling
- Configurable timeout behavior
- Idempotency handling for order requests
- Outbox-based event publishing
- Kafka consumers for asynchronous processing
- Database migrations with Flyway
- Unit and service-level tests

These mechanisms improve reliability but do not, by themselves, guarantee exactly-once processing or eliminate every possible distributed-system failure.

## 🗄️ Database Design

Each service maintains its own database or database schema according to its responsibilities.

| Service | Primary data |
|---|---|
| User Service | Users, authentication and roles |
| Product Service | Products and categories |
| Order Service | Orders, statuses and outbox events |
| Inventory Service | Inventory records and outbox events |

Flyway migrations provide versioned database schema changes. Hibernate/JPA is used for entity persistence.

## 📌 REST API Overview

### Authentication

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/auth/register` | Register a user |
| POST | `/api/auth/login` | Authenticate a user |

### Products

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/getAllProducts` | Retrieve products |
| GET | `/api/product/{id}` | Retrieve a product |
| POST | `/api/createProduct` | Create a product |
| PUT | `/api/product/{id}` | Update a product |
| DELETE | `/api/product/{id}` | Delete a product |

### Orders

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/orders` | Place an order |

Order endpoints require the appropriate authentication and authorization. Use the exact routes and request formats defined by the current controller implementations.

## 🧪 Testing

The project contains tests for service logic, order processing, and Kafka event handling.

Run tests separately from each microservice directory:

```bash
mvn clean verify
```

Repeat this command in each service directory:

- `service-registry`
- `cloud-gateway`
- `user-service`
- `product-service`
- `order-service`
- `inventory-service`

A successful build verifies the checks configured in that module. Additional integration, security, concurrency, and failure-recovery tests are necessary before a production deployment.

## 🐳 Docker and Deployment

Dockerfiles and a Docker Compose configuration are included in the project.

From the repository root, you can try:

```bash
docker compose config
```

This validates the Compose configuration. To start the complete stack, first configure the required environment variables and verify that the Compose file includes all required services, database instances, Kafka, health checks, and dependencies.

Prometheus configuration is also included for monitoring-related setup.

## ▶️ Local Development

### Prerequisites

- JDK 17
- Maven
- MySQL 8
- Apache Kafka, when running event-driven workflows
- Git

### Start the services

Open separate terminals and run the following commands from the repository root.

**1. Eureka**

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

Ensure MySQL databases, environment variables, Kafka, and service configuration are ready before starting the services.

## 📍 Project Status

- ✅ Eureka service discovery
- ✅ API Gateway and service routing
- ✅ User authentication and JWT security
- ✅ Product management APIs
- ✅ Order creation and persistence
- ✅ OpenFeign integration
- ✅ Kafka event-processing components
- ✅ Outbox-related implementation
- ✅ Inventory service components
- ✅ Docker and Compose configuration files
- ✅ Flyway migration files
- 🟡 Production deployment and operational validation

The repository contains implementation and configuration for these capabilities. Their complete behavior should be confirmed through the relevant tests and deployment environment.

## 🔭 Potential Future Improvements

- Payment service integration
- Email and push notifications
- Kubernetes deployment
- CI/CD pipeline
- Centralized logging and distributed tracing
- Automated load and performance testing
- Production-grade secrets management
- Operational dashboards and alerting
- Backup, disaster recovery, and deployment rollback procedures

## 👨‍💻 Developer

**Rahul Dwivedi**

Java Backend / Full Stack Developer

**Core skills:** Java, Spring Boot, Spring Security, Microservices, REST APIs, MySQL, Hibernate/JPA, JWT, Apache Kafka, Docker, AWS.

---

⭐ If you find this project useful, consider giving the repository a star.
