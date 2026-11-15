# ShopSphere API Gateway

The **ShopSphere API Gateway** is the single entry point for client requests in the ShopSphere E-Commerce Microservices application. It is built using Java, Spring Boot, and Spring Cloud Gateway Server MVC.

It provides centralized request routing, JWT authentication, security controls, rate limiting, request filtering, and resilience features for downstream microservices.

## Key Features

- **Centralized Routing:** Routes incoming API requests to the appropriate microservices.
- **JWT Authentication:** Validates RS256 access tokens using the User Service's JWKS endpoint.
- **Token Validation:** Supports public-key-based JWT verification, including token claims validation.
- **CORS Configuration:** Provides centralized Cross-Origin Resource Sharing configuration.
- **Rate Limiting:** Controls incoming request rates using a token-bucket implementation.
- **Request Size Limiting:** Rejects requests that exceed the configured size limit.
- **Correlation ID:** Generates or propagates correlation IDs for request tracking across services.
- **Access Logging:** Provides request-level logging for monitoring and debugging.
- **Resilience:** Supports fallback handling and resilience configuration for downstream failures.
- **Standardized Error Responses:** Uses structured Problem Details responses.
- **Docker Support:** Supports containerized deployment.
- **Kubernetes Support:** Includes Kubernetes deployment configuration.

## Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Programming language |
| Spring Boot 3.x | Application framework |
| Spring Cloud Gateway Server MVC | API gateway and routing |
| Spring Security | Security configuration |
| Spring Security OAuth2 Resource Server | JWT validation |
| JWKS | Public key distribution |
| Maven | Build and dependency management |
| Eureka | Service discovery |
| Resilience4j | Resilience patterns |
| Docker | Containerization |
| Kubernetes | Container orchestration |
| JUnit 5 | Unit and integration testing |
| Mockito | Mock-based testing |
| Actuator | Application monitoring |
| Prometheus | Metrics collection |

## Architecture

```text
                   Client / Frontend
                          |
                          |
                          v
                +-------------------+
                |   API Gateway     |
                |    Port: 8080     |
                +-------------------+
                          |
             +------------+------------+
             |            |            |
             v            v            v
        Authentication  Routing    Security Filters
        JWT / JWKS      & Eureka    Rate Limiting
             |            |        CORS / Logging
             |            |        Request Validation
             |            |
             +------------+
                          |
             +------------+-------------+
             |            |             |
             v            v             v
        User Service  Product Service  Order Service
          :8081          :PORT           :PORT
             |            |             |
             v            v             v
          MySQL         MySQL          MySQL
```

The Gateway acts as the centralized entry point and forwards requests to the appropriate downstream service.

## Request Processing Flow

1. A client sends an HTTP request to the API Gateway.
2. The Gateway applies configured request filters.
3. Security configuration determines whether authentication is required.
4. Protected requests undergo JWT validation using the configured JWKS endpoint.
5. Request validation, rate limiting, and other configured filters are applied.
6. The Gateway matches the request against configured routes.
7. The request is forwarded to the appropriate microservice.
8. The downstream response is returned to the client.

## Security

### JWT Authentication with JWKS

The Gateway validates access tokens issued by the User Service.

- Uses RS256 public-key verification.
- Retrieves public keys from the JWKS endpoint.
- Supports key identification through the JWT `kid` header.
- Validates configured token claims, including issuer and audience.
- Rejects invalid or expired tokens on protected routes.
- Keeps token-signing private keys outside the Gateway.

Example JWKS endpoint:

```text
http://localhost:8081/.well-known/jwks.json
```

The actual endpoint and token validation settings are configurable.

### CORS

CORS is configured centrally to control which frontend origins, HTTP methods, and headers are allowed to access the Gateway APIs.

### Rate Limiting

The Gateway uses a token-bucket-based rate-limiting implementation to control incoming traffic.

When the configured request limit is exceeded, the Gateway can return:

```text
HTTP 429 - Too Many Requests
```

### Request Size Limiting

The request-size filter protects the Gateway from excessively large incoming requests by enforcing configured size limits.

## Request Tracking and Logging

The Gateway includes request tracking and logging components.

| Component | Responsibility |
|---|---|
| CorrelationIdFilter | Generates or propagates correlation IDs |
| AccessLogFilter | Records request-related information |
| HeaderRequestWrapper | Handles configured request-header processing |
| ProblemWriter | Writes standardized error responses |
| RateLimitFilter | Enforces request-rate policies |
| RequestSizeLimitFilter | Controls incoming request size |
| TokenBucket | Implements token-bucket rate calculation |

Correlation IDs help trace a request across multiple microservices.

## Resilience and Fallback Handling

The Gateway includes resilience configuration and fallback handling for downstream service failures.

These mechanisms help provide controlled responses when a dependent service is unavailable or a request cannot be completed successfully.

The exact retry, timeout, and circuit-breaker behaviour depends on the configured resilience policies.

## Project Structure

```text
cloud-gateway/
│
├── src/
│   ├── main/
│   │   ├── java/com/shopsphere/cloud/
│   │   │   │
│   │   │   ├── CloudGatewayApplication.java
│   │   │   │
│   │   │   ├── config/
│   │   │   │   ├── GatewayProperties.java
│   │   │   │   ├── ResilienceConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── SecurityJwtProperties.java
│   │   │   │
│   │   │   ├── controller/
│   │   │   │   └── FallbackController.java
│   │   │   │
│   │   │   ├── security/
│   │   │   │   └── JwtDecoderFactory.java
│   │   │   │
│   │   │   └── web/
│   │   │       ├── AccessLogFilter.java
│   │   │       ├── CorrelationIdFilter.java
│   │   │       ├── HeaderRequestWrapper.java
│   │   │       ├── ProblemWriter.java
│   │   │       ├── RateLimitFilter.java
│   │   │       ├── RequestSizeLimitFilter.java
│   │   │       └── TokenBucket.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   │
│   └── test/
│       ├── java/com/shopsphere/cloud/
│       │   ├── controller/
│       │   ├── security/
│       │   └── web/
│       │
│       └── resources/
│           └── application.properties
│
├── k8s/
│   └── gateway.yaml
│
├── Dockerfile
├── pom.xml
└── README.md
```

## Prerequisites

Make sure the following tools are installed:

- Java 17 or later
- Maven 3.8+
- Docker
- MySQL (for the dependent services)
- Running User Service and other required downstream services
- Eureka Service Registry, if service discovery is enabled

## Configuration

Application configuration is maintained in:

```text
src/main/resources/application.yml
```

Local development configuration:

```text
src/main/resources/application-local.yml
```

Configure the following values through the application's supported configuration properties or environment variables:

| Configuration | Description |
|---|---|
| JWT JWKS URI | User Service public-key endpoint |
| JWT Issuer | Expected token issuer |
| JWT Audience | Expected token audience |
| Eureka URL | Service Registry endpoint |
| Gateway Port | Public API port |
| Actuator Port | Internal monitoring port |
| CORS Origins | Allowed frontend origins |
| Rate Limit | Configured request-rate policy |
| Request Size Limit | Maximum permitted request size |

**Important:** Never commit private keys, passwords, database credentials, or production secrets to GitHub.

## Running Locally

### 1. Clone the Repository

```bash
git clone https://github.com/code-with-rahuldwivedi/CommerceHub.git
```

### 2. Navigate to API Gateway

```bash
cd CommerceHub/cloud-gateway
```

### 3. Build the Application

```bash
mvn clean package
```

### 4. Run the Application

```bash
mvn spring-boot:run
```

To activate the local profile:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The Gateway requires its configured dependencies and downstream services to be available for end-to-end request routing.

## Docker Deployment

### Build Docker Image

```bash
docker build -t shopsphere-api-gateway:latest .
```

### Run Docker Container

```bash
docker run -d \
  --name shopsphere-api-gateway \
  -p 8080:8080 \
  shopsphere-api-gateway:latest
```

Supply the required environment variables and service connectivity settings according to your deployment configuration.

## Kubernetes Deployment

A Kubernetes manifest is available at:

```text
k8s/gateway.yaml
```

Apply the manifest using:

```bash
kubectl apply -f k8s/gateway.yaml
```

Check deployment status:

```bash
kubectl get deployments
kubectl get pods
kubectl get services
```

## Monitoring and Health Checks

Spring Boot Actuator provides application health and monitoring endpoints.

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Application health |
| `/actuator/info` | Application information |
| `/actuator/prometheus` | Prometheus metrics |

The public API runs on port `8080`. The Actuator management port is configured separately and should remain internal to the deployment environment.

## Testing

The project includes unit and security tests using JUnit 5 and Mockito.

Important test areas include:

- Gateway security configuration
- JWT decoder creation and validation
- Fallback controller behaviour
- Correlation ID handling
- Rate-limiting behaviour
- Request-size validation

Run tests:

```bash
mvn test
```

Run the complete Maven verification lifecycle:

```bash
mvn clean verify
```

## API Access

The Gateway exposes the configured microservice routes through a single entry point.

Example base URL:

```text
http://localhost:8080
```

Requests are routed according to the path predicates and destination URIs configured in `application.yml`.

Refer to the application route configuration for the exact API paths and downstream service mappings.

## Design Benefits

- Provides a single entry point for multiple microservices.
- Centralizes authentication and security enforcement.
- Reduces client-side service discovery complexity.
- Improves request observability through correlation IDs and access logs.
- Helps control incoming traffic through rate limiting.
- Provides consistent error handling and fallback behaviour.
- Supports containerized and Kubernetes-based deployment.

## Future Improvements

- Distributed rate limiting using a shared store.
- Enhanced distributed tracing and observability.
- Additional integration and load testing.
- Centralized secrets management.
- Automated deployment pipelines.
- Further resilience and fault-tolerance improvements.

## Author

**Rahul Dwivedi**

Java Backend Developer

**Project:** ShopSphere – E-Commerce Microservices Backend

**GitHub:** https://github.com/code-with-rahuldwivedi/CommerceHub

---

*ShopSphere is a microservices-based e-commerce backend project focused on secure API communication, service routing, reliability, and maintainable backend architecture.*
