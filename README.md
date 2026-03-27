# Bank Auth Service

Microservice d'authentification pour une architecture bancaire microservices. Gère l'inscription, la validation JWT et la publication d'événements — le login passe directement par Keycloak.

Stack : Spring Boot 4, Keycloak, Kafka, PostgreSQL. Déployé sur AWS EC2 via GitLab CI/CD.

---

## Service disponible

Le service tourne sur AWS EC2 — tu peux tester l'API directement sans rien installer :

```
GET  http://44.201.182.74:8082/actuator/health
POST http://44.201.182.74:8081/api/auth/register
```

Exemple curl :

```bash
curl -X POST http://44.201.182.74:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"test@bank.com","password":"Test1234!","firstName":"Test","lastName":"User"}'
```

---

## Architecture

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

## Stack

| Technologie | Version | Rôle |
|---|---|---|
| Spring Boot | 4.0.3 | Framework principal |
| Spring Security | 7.x | OAuth2 Resource Server |
| Keycloak | 23.0.0 | Serveur d'authentification SSO |
| Spring Cloud Gateway | 2023.0.3 | API Gateway + routing |
| Apache Kafka | 7.5.0 | Event streaming |
| PostgreSQL | 15 | Persistance |
| Docker | — | Containerisation |
| GitLab CI/CD | — | Pipeline build / test / deploy |
| AWS ECR | — | Registre d'images Docker |
| AWS EC2 | t3.small | Hébergement production |
| Swagger / OpenAPI | 3 | Documentation API |

---

## Fonctionnalités

- Register : crée l'utilisateur dans Keycloak via Admin REST API + profil en DB + auto-login
- Login : délégué à Keycloak (OAuth2 Password Flow), retourne access_token RS256
- Token validation : API Gateway valide le JWT Keycloak (RS256) avant de router
- User profile : endpoint protégé qui retourne les infos de l'utilisateur connecté
- Kafka events : event `USER_REGISTERED` publié sur `user.registered` à chaque inscription
- Rate limiting : 5 requêtes/minute par IP sur les routes `/api/auth`
- CORS : configuré pour Angular (port 4200)

---

## Endpoints

### Auth — public

| Méthode | Endpoint | Auth |
|---|---|---|
| `POST` | `/api/auth/register` | ❌ Public |

Body :
```json
{
  "email": "john@bank.com",
  "password": "Test1234!",
  "firstName": "John",
  "lastName": "Doe"
}
```

Réponse :
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

### User — protégé

| Méthode | Endpoint | Auth |
|---|---|---|
| `GET` | `/api/user/me` | ✅ Bearer Token |

Réponse :
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

## Choix techniques

**Distributed transaction rollback** — Keycloak et PostgreSQL sont deux systèmes séparés. `@Transactional` couvre PostgreSQL mais pas Keycloak. Si la DB échoue après la création Keycloak, `deleteKeycloakUser()` est appelé manuellement pour éviter l'incohérence.

**Auto-login après register** — après inscription, le service appelle directement `/token` Keycloak et retourne les tokens au client. L'utilisateur n'a pas besoin de faire un second appel.

**RS256 vs HS256** — les tokens sont signés avec une clé asymétrique (Keycloak). L'API Gateway valide avec la clé publique sans jamais avoir la clé privée.

**Variables d'environnement** — `application.yaml` utilise `${VAR:valeur_locale}` pour toutes les URLs et secrets. En local Spring utilise les valeurs par défaut. En prod Docker Compose injecte les vraies valeurs au démarrage du conteneur. Le fichier de config ne contient aucun secret.

---

## CI/CD et déploiement

À chaque push sur `main`, le pipeline GitLab fait quatre choses dans l'ordre :

1. Compile le projet avec Maven
2. Lance les tests JUnit avec PostgreSQL réel en conteneur
3. Build l'image Docker
4. Pousse l'image sur AWS ECR

Sur EC2, le déploiement se fait manuellement :

```bash
docker-compose pull && docker-compose down && docker-compose up -d
```

Les services (auth-service, PostgreSQL, Keycloak) tournent via Docker Compose. Les secrets sont injectés comme variables d'environnement — ils ne sont jamais dans le code ni dans GitLab.

---

## Sécurité

- JWT RS256 — tokens signés par Keycloak (clé asymétrique)
- OAuth2 Resource Server — API Gateway + auth-service délèguent la validation à Keycloak
- Stateless — aucune session serveur, `SessionCreationPolicy.STATELESS`
- Rate limiting — 5 requêtes/minute par IP sur les routes `/api/auth`
- CORS — origines autorisées configurées explicitement
- BCrypt — mots de passe hashés par Keycloak, jamais stockés en clair

---

## Lancer le projet

Prérequis : Java 17+, Docker Desktop, IntelliJ IDEA

### 1. Infrastructure

```bash
cd infrastructure
docker-compose up -d
```

Lance : PostgreSQL (5432, 5433, 5434), Keycloak (8180), Kafka + Zookeeper (9092)

### 2. Keycloak

1. Ouvre `http://localhost:8180` → admin/admin
2. Crée le realm `bank-app`
3. Crée le client `api-gateway` (Direct Access Grants activé)
4. Crée les rôles `USER` et `ADMIN`

### 3. Services

```bash
cd bank-auth-service && mvn spring-boot:run
cd notification-service && mvn spring-boot:run
cd api-gateway && mvn spring-boot:run
```

---

## Structure

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

Tags : `v1.0.0` — authentification complète avec Keycloak

---

## Documentation API

Swagger UI : `http://44.201.182.74:8081/swagger-ui/index.html`

---

## Auteur

Achraf Ait M'Barek — the-crazy-achraf@hotmail.fr