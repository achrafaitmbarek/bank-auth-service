---

# Bank Auth Service

Ce microservice gère l'authentification et l'identité au sein de la **Bank Platform**. Il assure l'inscription, la persistance des profils et la communication événementielle via Kafka, tout en déléguant la gestion des tokens à Keycloak.

**État du déploiement :** Pipeline GitOps 100% automatisé sur AWS.

---

## Architecture Système

Le schéma ci-dessous illustre l'intégration du service dans l'écosystème. La sécurité est assurée par une validation de token asymétrique (RS256) au niveau de la Gateway.

```text
Client / Postman
      │
      ▼
┌─────────────────────┐
│    API Gateway      │  :8080  Spring Cloud Gateway
│  OAuth2 RS (RS256)  │         Vérifie les signatures JWT
└──────────┬──────────┘
           │ Proxying
     ┌─────┴─────────────────────┐
     ▼                           ▼
┌─────────┐              ┌──────────────┐
│  Auth   │              │ Notification │
│ Service │              │   Service    │
│  :8081  │              │   :8082      │
└────┬────┘              └──────────────┘
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

## Service en Production (AWS)

Le service est exposé sur une instance EC2. Vous pouvez tester les endpoints de santé et d'inscription directement :

* **Health Check** : `GET http://44.201.182.74:8081/actuator/health`
* **Documentation** : `http://44.201.182.74:8081/swagger-ui/index.html`

**Test d'inscription rapide :**
```bash
curl -X POST http://44.201.182.74:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@bank.com","password":"Test1234!","firstName":"Achraf","lastName":"Ait"}'
```

---

## Choix Techniques & Sécurité

Pour répondre aux standards bancaires, plusieurs patterns ont été implémentés :

* **Transactionnalité Distribuée** : Keycloak et PostgreSQL étant isolés, une logique de rollback manuel (`deleteKeycloakUser()`) est en place pour garantir la cohérence des données si l'insertion en base échoue après la création du compte IAM.
* **Sécurité Stateless** : Aucune session serveur. Validation des JWT via le endpoint JWKS de Keycloak (clés asymétriques).
* **Protection des Endpoints** : Implémentation de Rate Limiting (5 req/min par IP) sur les routes sensibles pour prévenir les attaques par déni de service ou brute-force.

---

## CI/CD & Déploiement

Le cycle de vie du code est entièrement automatisé via GitLab CI/CD.

1.  **Maven Build & Test** : Compilation et validation via JUnit (Postgres en conteneur pour les tests).
2.  **Containerisation** : Construction de l'image Docker et stockage sur **AWS ECR**.
3.  **Déploiement Automatisé** : Connexion SSH sécurisée à l'instance EC2, mise à jour du code via le dépôt `infrastructure` et redémarrage à chaud des services.

---

## Stack Technique

| Technologie | Rôle |
| :--- | :--- |
| **Spring Boot 4.0.3** | Framework métier |
| **Keycloak 23.0** | Serveur IAM / SSO |
| **Kafka 7.5.0** | Event streaming (Confluent) |
| **PostgreSQL 15** | Persistance des profils |
| **AWS ECR / EC2** | Hébergement et registre |

---

## Guide de Développement Local

### 1. Lancer l'infrastructure
Clonez le dépôt centralisé pour démarrer la base de données, Keycloak et Kafka :
```bash
git clone https://gitlab.com/votre-username/infrastructure.git
cd infrastructure
docker-compose up -d
```

### 2. Démarrer le service
```bash
cd bank-auth-service
mvn spring-boot:run
```

---

## Auteur

Achraf Ait M'Barek — the-crazy-achraf@hotmail.fr

---