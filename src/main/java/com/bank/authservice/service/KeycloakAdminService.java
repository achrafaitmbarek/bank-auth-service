package com.bank.authservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakAdminService {

    private final RestTemplate restTemplate;

    @Value("${keycloak.admin.server-url}")
    private String serverUrl;

    @Value("${keycloak.admin.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${keycloak.admin.client-id}")
    private String clientId;
    /**
     * Étape 1 : Récupérer un token admin depuis Keycloak.
     * On utilise le realm "master" avec le client "admin-cli" (client par défaut de Keycloak).
     * C'est comme un "super login" pour pouvoir gérer les users via l'API Admin.
     */
    private String getAdminToken() {
        String tokenUrl = serverUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");   // client admin intégré à Keycloak
        body.add("username", adminUsername);   // admin / admin (défini dans docker-compose)
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        String token = (String) response.getBody().get("access_token");

        log.debug("Admin token retrieved from Keycloak");
        return token;
    }

    /**
     * Étape 2 : Créer un utilisateur dans le realm bank-app via l'API Admin REST Keycloak.
     *
     * Keycloak Admin REST API : POST /admin/realms/{realm}/users
     * Retourne 201 Created avec un header Location: .../users/{keycloakId}
     * On extrait l'UUID du user depuis ce header.
     *
     * @return keycloakId (UUID) du user créé
     */
    public String createUser(String email, String firstName, String lastName, String password) {
        String adminToken = getAdminToken();
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken); // Authorization: Bearer <adminToken>

        // Credential : le mot de passe de l'utilisateur
        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", password,
                "temporary", false  // false = l'user n'est pas forcé de changer son mdp
        );

        // UserRepresentation : structure attendue par Keycloak Admin API
        Map<String, Object> userRepresentation = Map.of(
                "username", email,          // username = email dans notre cas
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,            // compte actif immédiatement
                "emailVerified", true,      // pas de vérification email pour le dev
                "credentials", List.of(credential)
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(usersUrl, request, Void.class);

            // Keycloak retourne 201 Created
            // Le header Location contient l'URL du user créé :
            // http://localhost:8180/admin/realms/bank-app/users/5f3fdc67-0401-4b20-...
            URI location = response.getHeaders().getLocation();
            String[] parts = location.getPath().split("/");
            String keycloakId = parts[parts.length - 1]; // dernier segment = UUID

            log.info("User created in Keycloak with id: {}", keycloakId);
            return keycloakId;

        } catch (HttpClientErrorException.Conflict e) {
            // 409 Conflict = email déjà utilisé dans Keycloak
            throw new RuntimeException("Email already exists in Keycloak: " + email);
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak", e);
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage());
        }

    }
    public Map<String, Object> getTokenForUser(String email, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);  // "api-gateway"
        body.add("username", email);
        body.add("password", password);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
            log.info("Auto-login token retrieved for: {}", email);
            return response.getBody();
        } catch (Exception e) {
            log.error("Auto-login failed for: {}", email, e);
            throw new RuntimeException("Auto-login failed after registration");
        }
    }
}