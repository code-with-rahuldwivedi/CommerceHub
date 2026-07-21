# 🛒 ShopSphere - E-Commerce Microservices Backend

ShopSphere is a scalable E-Commerce backend application built using **Java, Spring Boot, Spring Security, JWT, MySQL, Eureka Service Discovery, API Gateway, and Microservices Architecture**. The project follows industry-standard practices with separate microservices for different business domains.

---

## 🚀 Tech Stack

* Java 17
* Spring Boot 3
* Spring Security
* Spring Data JPA
* JWT Authentication
* MySQL
* Maven
* REST API
* Spring Cloud Gateway
* Eureka Service Discovery
* OpenFeign
* Resilience4j Circuit Breaker
* Microservices Architecture

---

## 📂 Project Structure

```text
ShopSphere-Microservices/
│
├── service-registry/      # Eureka Server
├── cloud-gateway/         # API Gateway
├── user-service/          # User Authentication & Authorization
├── product-service/       # Product Management
├── order-service/         # Order Management
└── README.md
```

---

## ✅ Completed Services

### 1️⃣ Service Registry (Eureka)

* Service Registration
* Service Discovery
* Centralized service management
* Dynamic microservice communication

Runs on: `http://localhost:8761`

---

### 2️⃣ API Gateway

* Central entry point for all APIs
* Route management
* JWT Authentication & Authorization
* Role-based access control
* Resilience4j Circuit Breaker
* Timeout handling

Runs on: `http://localhost:8080`

---

### 3️⃣ User Service

* User Registration
* User Login
* BCrypt Password Encryption
* JWT Token Generation
* JWT Validation
* Role-Based Authorization
* Spring Security
* MySQL Integration

#### Roles

* ADMIN
* USER

Runs on: `http://localhost:8081`

---

### 4️⃣ Product Service

* Add Product
* Update Product
* Delete Product
* Get All Products
* Get Product By ID
* Product Validation
* JWT Verification
* Role-Based Access Control

#### Authorization

| API               | Access |
| ----------------- | ------ |
| GET Products      | Public |
| GET Product By ID | Public |
| POST Product      | ADMIN  |
| PUT Product       | ADMIN  |
| DELETE Product    | ADMIN  |

Runs on: `http://localhost:8082`

---

### 5️⃣ Order Service

* Place Order
* Order Persistence
* Product Price Fetch using Feign Client
* Total Price Calculation
* Order Status Management
* JWT Authentication via API Gateway

#### Example

* Product Price: ₹79,999
* Quantity: 2
* Total Price: ₹1,59,998

Runs on: `http://localhost:8083`

---

## 🔄 Microservices Communication

The Order Service communicates with Product Service using **OpenFeign** to fetch product details and calculate the total order price dynamically.

```text
Client
   ↓
API Gateway
   ↓
Order Service ──Feign──▶ Product Service
```

---

## 🔐 Security

* Spring Security
* JWT Authentication
* Role-Based Authorization
* Stateless Session Management
* Password Encryption using BCrypt
* API Gateway Security Filter

---

## 🛡 Resilience4j Circuit Breaker

Implemented Circuit Breaker for:

* USER-SERVICE
* PRODUCT-SERVICE
* ORDER-SERVICE

Features:

* Failure rate monitoring
* Automatic fallback
* Timeout handling
* Health indicators

---

## 🗄 Database

### User Database

* Users
* Roles

### Product Database

* Products
* Categories

### Order Database

* Orders
* Order Status

---

## 📌 REST APIs

### Authentication APIs

| Method | Endpoint             |
| ------ | -------------------- |
| POST   | `/api/auth/register` |
| POST   | `/api/auth/login`    |

### Product APIs

| Method | Endpoint              | Access |
| ------ | --------------------- | ------ |
| GET    | `/api/getAllProducts` | Public |
| GET    | `/api/product/{id}`   | Public |
| POST   | `/api/createProduct`  | ADMIN  |
| PUT    | `/api/product/{id}`   | ADMIN  |
| DELETE | `/api/product/{id}`   | ADMIN  |

### Order APIs

| Method | Endpoint      | Access       |
| ------ | ------------- | ------------ |
| POST   | `/api/orders` | USER / ADMIN |

---

## ▶ Running the Project

### 1. Clone Repository

```bash
git clone https://github.com/code-with-rahuldwivedi/ShopSphere-Microservices.git
```

### 2. Start Service Registry

```bash
cd service-registry
mvn spring-boot:run
```

### 3. Start User Service

```bash
cd user-service
mvn spring-boot:run
```

### 4. Start Product Service

```bash
cd product-service
mvn spring-boot:run
```

### 5. Start Order Service

```bash
cd order-service
mvn spring-boot:run
```

### 6. Start API Gateway

```bash
cd cloud-gateway
mvn spring-boot:run
```

---

## 📈 Project Status

* ✅ Service Registry (Eureka) Completed
* ✅ API Gateway Completed
* ✅ User Service Completed
* ✅ Product Service Completed
* ✅ Order Service Completed
* ✅ JWT Authentication Completed
* ✅ Role-Based Authorization Completed
* ✅ Feign Client Integration Completed
* ✅ Resilience4j Circuit Breaker Completed
* 🟡 Stock Reduction Feature - In Progress

---

## 🛠 Upcoming Features

* Automatic Stock Reduction
* Inventory Service
* Payment Service
* Notification Service
* Docker Containerization
* Kubernetes Deployment
* AWS Deployment
* CI/CD Pipeline

---

## 👨‍💻 Developer

**Rahul Dwivedi**

Java Full Stack Developer

### Skills

* Java
* Spring Boot
* Spring Security
* Microservices
* REST APIs
* MySQL
* Hibernate
* JWT
* Docker
* AWS

---

⭐ If you like this project, don't forget to give it a **Star** on GitHub.
