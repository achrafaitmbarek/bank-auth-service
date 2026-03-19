# 🏦 Bank Auth Service

Microservice d'authentification bancaire basé sur **Keycloak**, **Spring Boot 4** et **Kafka**.
Partie du projet **Bank Platform** — architecture microservices orientée fintech/ESN.

---

## Architecture globale

```
Client / Postman
      │
      ▼
┌─────────────────────┐
│    API Gateway       │  :8080  Spring Cloud Gateway
│  OAuth2 RS (RS256)  │         Valide les tokens Keycloak
└──────────┬──────────┘
           │ route
     ┌─────┴──────┐
     ▼            ▼
┌─────────┐  ┌──────────────┐
│  Auth   │  │ Notification │
│ Service │  │   Service    │
│  :8081  │  │   :8082      │
└────┬────┘  └──────────────┘
     │ Kafka (user.registered)    ▲
     └────────────────────────────┘
     │
     ▼
┌──────────┐    ┌──────────────┐
│ Keycloak │    │  PostgreSQL  │
│  :8180   │    │  authdb:5432 │
│ bank-app │    │ user_profile │
└──────────┘    └──────────────┘
```

---

## Stack technique

| Technologie | Version | Rôle |
|---|---|---|
| Spring Boot | 4.0.3 | Framework principal |
| Spring Security | 7.x | OAuth2 Resource Server |
| Keycloak | 23.0.0 | Serveur d'authentification SSO |
| Spring Cloud Gateway | 2023.0.3 | API Gateway + routing |
| Apache Kafka | 7.5.0 | Event streaming |
| PostgreSQL | 15 | Persistance |
| Docker Compose | — | Infrastructure locale |
| Swagger / OpenAPI | 3 | Documentation API |
| Lombok | — | Réduction boilerplate |

---

## Fonctionnalités

- **Register** — crée l'utilisateur dans Keycloak via Admin REST API + profil en DB + auto-login
- **Login** — délégué à Keycloak (OAuth2 Password Flow), retourne access_token RS256
- **Token validation** — API Gateway valide le JWT Keycloak (RS256) avant de router
- **User profile** — endpoint protégé qui retourne les infos de l'utilisateur connecté
- **Kafka events** — event `USER_REGISTERED` publié sur `user.registered` à chaque inscription
- **Rate limiting** — protection contre les abus via filtre custom
- **CORS** — configuré pour Angular (port 4200)

---

## Endpoints

### Auth Controller — public

| Méthode | Endpoint | Description | Auth |
|---|---|---|---|
| `POST` | `/api/auth/register` | Inscription + auto-login Keycloak | ❌ Public |

**Body Register :**
```json
{
  "email": "john@bank.com",
  "password": "Test1234!",
  "firstName": "John",
  "lastName": "Doe"
}
```

**Réponse Register :**
```json
{
  "message": "Registration successful.",
  "email": "john@bank.com",
  "keycloakId": "a70bdb62-ed0f-468d-ad97-93698bc0c287",
  "accessToken": "eyJhbGciOiJSUzI1NiIs...",
  "refreshToken": "eyJhbGciOiJIUzI1NiIs...",
  "expiresIn": 300
}
```

### Login — via Keycloak directement

```
POST http://localhost:8180/realms/bank-app/protocol/openid-connect/token
Content-Type: application/x-www-form-urlencoded

client_id=api-gateway&username=john@bank.com&password=Test1234!&grant_type=password
```

### User Controller — protégé

| Méthode | Endpoint | Description | Auth |
|---|---|---|---|
| `GET` | `/api/user/me` | Profil de l'utilisateur connecté | ✅ Bearer Token |

**Réponse /me :**
```json
{
  "email": "john@bank.com",
  "message": "You are authenticated"
}
```

---

## Flow d'authentification

```
1. POST /api/auth/register
   └── auth-service → Keycloak Admin API (crée le user)
   └── auth-service → PostgreSQL (sauvegarde user_profile)
   └── auth-service → Kafka (event USER_REGISTERED)
   └── auth-service → Keycloak /token (auto-login)
   └── Retourne { accessToken, refreshToken, keycloakId }

2. GET /api/user/me  (avec Bearer token)
   └── API Gateway valide le JWT RS256 via Keycloak JWKS
   └── Route vers auth-service
   └── auth-service lit jwt.getClaim("email")
   └── Retourne { email, message }
```

---

## Sécurité

- **JWT RS256** — tokens signés par Keycloak (clé asymétrique)
- **OAuth2 Resource Server** — API Gateway + auth-service délèguent la validation à Keycloak
- **Stateless** — aucune session serveur, SessionCreationPolicy.STATELESS
- **Rate Limiting** — filtre custom limité par IP
- **CORS** — origines autorisées configurées explicitement
- **BCrypt** — mots de passe hashés par Keycloak (jamais stockés en clair)

---

## Design Patterns

| Pattern | Implémentation |
|---|---|
| API Gateway | Spring Cloud Gateway — point d'entrée unique |
| Event-Driven | Kafka Producer/Consumer — découplage des services |
| DTO Pattern | RegisterRequest, AuthResponse — séparation entité/API |
| Repository Pattern | UserProfileRepository — abstraction de la persistance |
| Dependency Injection | @RequiredArgsConstructor — injection par constructeur |

---

## Lancer le projet

### Prérequis
- Java 17+
- Docker Desktop
- IntelliJ IDEA

### 1. Démarrer l'infrastructure

```bash
cd infrastructure
docker-compose up -d
```

Services démarrés : PostgreSQL auth (5432), notification (5433), keycloak (5434), Keycloak (8180), Kafka + Zookeeper (9092)

### 2. Configurer Keycloak

1. Ouvre `http://localhost:8180` → admin/admin
2. Crée le realm `bank-app`
3. Crée le client `api-gateway` (Direct Access Grants activé)
4. Crée les rôles `USER` et `ADMIN`

### 3. Lancer les services dans l'ordre

```bash
cd bank-auth-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

---

## Structure du projet

```
bank-auth-service/
├── config/
│   ├── SecurityConfig.java        # OAuth2 Resource Server + CORS + Rate Limiting
│   ├── KafkaConfig.java           # KafkaTemplate + ObjectMapper beans
│   ├── RestTemplateConfig.java    # Client HTTP pour Keycloak Admin API
│   └── SwaggerConfig.java
├── controller/
│   ├── AuthController.java        # POST /api/auth/register
│   └── UserController.java        # GET /api/user/me
├── domain/
│   └── UserProfile.java           # Entité JPA (id = Keycloak UUID)
├── dto/
│   ├── RegisterRequest.java
│   └── AuthResponse.java
├── event/
│   └── UserRegisteredEvent.java   # Payload Kafka
├── exception/
│   ├── GlobalExceptionHandler.java
│   ├── ApiException.java
│   └── ApiError.java
├── repository/
│   └── UserProfileRepository.java
└── service/
    ├── AuthService.java            # Logique métier register
    ├── KeycloakAdminService.java   # Keycloak Admin REST API
    └── KafkaProducerService.java   # Publication events Kafka
```

---

## Gitflow

```
main     ← production (protégé, merge request uniquement)
develop  ← intégration continue
feature/ ← fonctionnalités en cours
```

**Tags :** `v1.0.0` — authentification complète avec Keycloak

---

## Documentation API

Swagger UI : `http://localhost:8081/swagger-ui.html`

---

## Auteur

**Achraf Ait Mbarek** — Développeur Backend Java/Spring
Projet portfolio bancaire