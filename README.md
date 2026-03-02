# 🔐 Auth Service — Banking-Grade Authentication Microservice

> A production-ready JWT authentication microservice built with Spring Boot, following the architecture and standards used in French banking and fintech environments.

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen?logo=spring)
![Spring Security](https://img.shields.io/badge/Spring%20Security-7.x-brightgreen?logo=springsecurity)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Compose-blue?logo=docker)
![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 📌 Overview

This service handles the full authentication lifecycle for a banking platform:

- **User registration** with BCrypt password hashing
- **JWT-based login** (short-lived access token + long-lived refresh token)
- **Token refresh** with single-use revocation strategy
- **Secure logout** that invalidates the refresh token server-side
- **Rate limiting** to protect against brute-force attacks
- **Role-based access control** (USER / ADMIN)

Built to reflect real-world constraints in regulated environments: no data leaks via DTOs, full transaction integrity via `@Transactional`, structured logging, and externalized secrets.

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                        CLIENT                           │
│              (Postman / Frontend / Mobile)              │
└───────────────────────────┬─────────────────────────────┘
                            │ HTTPS
                            ▼
┌─────────────────────────────────────────────────────────┐
│               SPRING SECURITY FILTER CHAIN              │
│                                                         │
│  1. RateLimitFilter   → 5 req/min per IP (Bucket4j)     │
│  2. JwtAuthFilter     → validates Bearer token          │
│  3. SecurityConfig    → route-level access rules        │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                    CONTROLLER LAYER                     │
│         AuthController  │  UserController               │
│    (no business logic — only HTTP handling)             │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                     SERVICE LAYER                       │
│          AuthService         │  JwtService              │
│   (all business decisions — @Transactional)             │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                   REPOSITORY LAYER                      │
│       UserRepository  │  RefreshTokenRepository         │
│            (Spring Data JPA — no raw SQL)               │
└───────────────────────────┬─────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────┐
│                  PostgreSQL (Docker)                    │
│              users  │  refresh_tokens                   │
└─────────────────────────────────────────────────────────┘
```

---

## 🛠️ Tech Stack

| Layer | Technology | Why |
|---|---|---|
| Framework | Spring Boot 4.0.3 | Standard in enterprise Java environments |
| Security | Spring Security 7.x | Industry-standard auth framework |
| JWT | jjwt 0.12.3 | Most widely used JWT library for Java |
| Database | PostgreSQL 16 | ACID-compliant, used extensively in fintech |
| ORM | Spring Data JPA + Hibernate 7 | Abstracts SQL, prevents injection |
| Rate Limiting | Bucket4j 8.10.1 | Token-bucket algorithm, used in production APIs |
| Documentation | SpringDoc OpenAPI 2.8.6 | Auto-generates Swagger UI |
| Password Hashing | BCrypt | Adaptive hashing with configurable cost factor |
| Testing | JUnit 5 + Mockito | Standard Java testing stack |
| Infrastructure | Docker + Compose | Portable, reproducible local environment |
| Build | Maven | Standard in Java enterprise projects |

---

## 📁 Project Structure

```
src/main/java/com/bank/authservice/
│
├── AuthServiceApplication.java       ← Entry point (@SpringBootApplication)
│
├── controller/
│   ├── AuthController.java           ← Public auth routes
│   └── UserController.java           ← Protected user routes
│
├── service/
│   ├── AuthService.java              ← Business logic (@Transactional)
│   └── JwtService.java               ← Token generation & validation
│
├── repository/
│   ├── UserRepository.java           ← User queries (JPA)
│   └── RefreshTokenRepository.java   ← Token revocation queries
│
├── domain/
│   ├── User.java                     ← @Entity — users table
│   ├── Role.java                     ← Enum: USER | ADMIN
│   └── RefreshToken.java             ← @Entity — refresh_tokens table
│
├── dto/
│   ├── RegisterRequest.java          ← Input validation (@NotBlank, @Email)
│   ├── LoginRequest.java
│   ├── RefreshTokenRequest.java
│   └── AuthResponse.java             ← Response — never expose domain entities
│
├── config/
│   ├── SecurityConfig.java           ← Filter chain, route access rules
│   ├── JwtAuthFilter.java            ← Validates JWT on every request
│   ├── RateLimitFilter.java          ← IP-based rate limiting
│   └── SwaggerConfig.java            ← OpenAPI / Bearer auth UI
│
└── exception/
    ├── ApiException.java             ← Custom RuntimeException with HttpStatus
    ├── ApiError.java                 ← Structured error response body
    └── GlobalExceptionHandler.java   ← @RestControllerAdvice — centralized errors
```

---

## 🔌 API Endpoints

### Public Routes

#### `POST /api/auth/register`
Register a new user.

**Request:**
```json
{
  "email": "ashraf@bank.com",
  "password": "SecurePass123",
  "firstName": "Ashraf",
  "lastName": "Eloken"
}
```

**Response `201 Created`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "email": "ashraf@bank.com",
  "role": "USER"
}
```

**Errors:** `409 Conflict` (email already exists), `400 Bad Request` (validation failed)

---

#### `POST /api/auth/login`
Authenticate with credentials.

**Request:**
```json
{
  "email": "ashraf@bank.com",
  "password": "SecurePass123"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "email": "ashraf@bank.com",
  "role": "USER"
}
```

**Errors:** `401 Unauthorized` (invalid credentials)

---

#### `POST /api/auth/refresh`
Get a new access token using a valid refresh token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response `200 OK`:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "new-uuid-here",
  "email": "ashraf@bank.com",
  "role": "USER"
}
```

**Errors:** `401 Unauthorized` (expired, revoked, or invalid refresh token)

---

#### `POST /api/auth/logout`
Revoke the current refresh token.

**Request:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response `204 No Content`**

---

### Protected Routes

> All protected routes require: `Authorization: Bearer <access_token>`

#### `GET /api/user/me`
Returns the authenticated user's info.

**Response `200 OK`:**
```json
{
  "email": "ashraf@bank.com"
}
```

**Errors:** `401 Unauthorized` (missing or invalid token)

---

## 🔒 Security Design

### Two-Token Strategy

```
ACCESS TOKEN (JWT)
  ├── Lifespan: 15 minutes
  ├── Storage: Client memory (never localStorage)
  ├── Signed with: HMAC-SHA256 (HS256)
  ├── Contains: email, role, iat, exp
  └── Stateless — server doesn't store it

REFRESH TOKEN (UUID)
  ├── Lifespan: 7 days
  ├── Storage: PostgreSQL (refresh_tokens table)
  ├── Revocable: revoked=true flag on logout
  └── Single-use: rotated on each /refresh call
```

### Why DTOs are mandatory in fintech

Direct entity exposure allows clients to inject `role: "ADMIN"` or `id: 1` fields. DTOs act as a strict contract — only the fields we explicitly define can enter or leave the system.

### Rate Limiting

5 requests per minute per IP address on all `/api/auth/**` routes. Uses the token-bucket algorithm (Bucket4j). Excess requests return `429 Too Many Requests`.

### Password Storage

BCrypt with Spring Security's default cost factor (10 rounds). Passwords are never stored in plain text or logged.

---

## 🚀 Run Locally

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Clone the repository

```bash
git clone git@gitlab.com:achrafaitmbarek/auth-service.git
cd auth-service
```

### 2. Configure environment variables

```bash
cp .env.example .env
```

Edit `.env` with your values:

```env
JWT_SECRET=your-256-bit-secret-key-here-minimum-32-chars
JWT_EXPIRATION=900000
REFRESH_TOKEN_EXPIRATION=604800000
DB_URL=jdbc:postgresql://localhost:5432/authdb
DB_USERNAME=authuser
DB_PASSWORD=your-db-password
```

### 3. Start the database

```bash
docker-compose up -d
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The API will be available at `http://localhost:8080`.

Swagger UI: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)

---

## 🧪 Running Tests

```bash
./mvnw test
```

Current test coverage:

| Test | Description |
|---|---|
| `register_success` | Creates user, returns 201 with tokens |
| `register_duplicateEmail_throwsConflict` | Returns 409 if email already exists |
| `login_success` | Valid credentials return access + refresh token |
| `login_emailNotFound_throwsUnauthorized` | Returns 401 if email doesn't exist |
| `login_wrongPassword_throwsUnauthorized` | Returns 401 if password doesn't match |
| `logout_success` | Revokes refresh token for valid user |
| `logout_userNotFound_throwsUnauthorized` | Returns 401 if user doesn't exist |

All tests use JUnit 5 + Mockito — no database required (pure unit tests with mocked repositories).

---

## 🎨 Design Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Repository Pattern** | `UserRepository`, `RefreshTokenRepository` | Decouples business logic from database access |
| **DTO Pattern** | `RegisterRequest`, `AuthResponse`, etc. | Prevents entity exposure, enforces API contract |
| **Builder Pattern** | `AuthResponse`, `ApiError` | Readable object construction, avoids telescoping constructors |
| **Chain of Responsibility** | Spring Security Filter Chain | Each filter handles one concern and passes to the next |
| **Strategy Pattern** | `UserDetailsService` implementation | Swappable auth strategies without changing the filter |
| **Singleton Pattern** | All Spring `@Bean` / `@Service` / `@Component` | One instance per application context |
| **Template Method** | `OncePerRequestFilter` | Base class defines the algorithm, subclass fills the details |

---

## 🌿 Git Workflow

This project follows **Gitflow**:

```
main          ← production-ready code only
  └── develop ← integration branch
        ├── feature/AUTH-01-project-setup
        ├── feature/AUTH-02-entities-and-repositories
        ├── feature/AUTH-03-jwt-service
        ├── feature/AUTH-04-auth-service
        ├── feature/AUTH-05-security-config
        ├── feature/AUTH-06-auth-controller
        ├── feature/AUTH-07-protected-routes
        ├── feature/AUTH-08-global-exception-handler
        ├── feature/AUTH-09-swagger-openapi
        ├── feature/AUTH-10-rate-limiting
        └── feature/README-portfolio
```

Each feature is developed in isolation, merged via Merge Request (GitLab) into `develop`, then released to `main`.

---

## 📋 Environment Variables Reference

| Variable | Description | Example |
|---|---|---|
| `JWT_SECRET` | HMAC-SHA256 signing key (min 32 chars) | `mySecretKey...` |
| `JWT_EXPIRATION` | Access token TTL in milliseconds | `900000` (15 min) |
| `REFRESH_TOKEN_EXPIRATION` | Refresh token TTL in milliseconds | `604800000` (7 days) |
| `DB_URL` | JDBC connection string | `jdbc:postgresql://localhost:5432/authdb` |
| `DB_USERNAME` | PostgreSQL user | `authuser` |
| `DB_PASSWORD` | PostgreSQL password | `changeme` |

> ⚠️ Never commit `.env` to version control. Only `.env.example` belongs in the repository.

---

## 📄 License

MIT — feel free to use this as a reference or starting point for your own projects.

---

*Built as a portfolio project to demonstrate production-ready Spring Boot patterns used in French banking and fintech environments.*