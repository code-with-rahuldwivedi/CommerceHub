# 🛒 CommerceHub - E-Commerce Microservices Backend

CommerceHub is a scalable E-Commerce backend application built using **Java, Spring Boot, Spring Security, JWT, MySQL, and Microservices Architecture**. The project is designed following industry-standard practices with separate microservices for different business domains.

---

## 🚀 Tech Stack

- Java 17
- Spring Boot
- Spring Security
- Spring Data JPA
- JWT Authentication
- MySQL
- Maven
- REST API
- Microservices Architecture

---

## 📂 Project Structure

```
CommerceHub/
│
├── user-service/
├── product-service/
└── README.md
```

---

# ✅ Completed Services

## 1️⃣ User Service

### Features

- User Registration
- User Login
- BCrypt Password Encryption
- JWT Authentication
- Role-Based Authorization
- Spring Security
- MySQL Integration

### Roles

- ADMIN
- USER

### Authentication

- JWT Token Generation
- JWT Validation
- Stateless Authentication

---

## 2️⃣ Product Service

### Features

- Add Product
- Update Product
- Delete Product
- Get All Products
- Get Product By Id
- Product Validation
- JWT Verification
- Role-Based Access Control

### Authorization

| API | Access |
|------|--------|
| GET Products | Public |
| GET Product By Id | Public |
| POST Product | ADMIN |
| PUT Product | ADMIN |
| DELETE Product | ADMIN |

---

# 🔐 Security

- Spring Security
- JWT Authentication
- Role-Based Authorization
- Stateless Session
- Password Encryption using BCrypt

---

# 🗄 Database

### User Database

- Users
- Roles

### Product Database

- Products
- Categories

---

# 📌 REST APIs

## User Service

| Method | Endpoint |
|---------|----------|
| POST | /api/auth/register |
| POST | /api/auth/login |

---

## Product Service

| Method | Endpoint |
|---------|----------|
| GET | /api/products |
| GET | /api/products/{id} |
| POST | /api/products |
| PUT | /api/products/{id} |
| DELETE | /api/products/{id} |

---

# ▶ Running the Project

### Clone Repository

```bash
git clone https://github.com/your-username/CommerceHub.git
```

### Start User Service

```bash
cd user-service
mvn spring-boot:run
```

Runs on:

```
http://localhost:8081
```

---

### Start Product Service

```bash
cd product-service
mvn spring-boot:run
```

Runs on:

```
http://localhost:8082
```

---

# 🛠 Upcoming Services

- API Gateway
- Eureka Service Discovery
- Order Service
- Inventory Service
- Payment Service
- Notification Service
- Docker
- Kubernetes
- AWS Deployment

---

# 📈 Project Status

✅ User Service Completed

✅ Product Service Completed

🟡 API Gateway - In Progress

---

# 👨‍💻 Developer

**Rahul**

Java Full Stack Developer

- Java
- Spring Boot
- Spring Security
- Microservices
- REST APIs
- MySQL
- Hibernate
- JWT

---

⭐ If you like this project, don't forget to give it a star.
