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


    private String getAdminToken() {
        String tokenUrl = serverUrl + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", "admin-cli");
        body.add("username", adminUsername);
        body.add("password", adminPassword);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);
        String token = (String) response.getBody().get("access_token");

        log.debug("Admin token retrieved from Keycloak");
        return token;
    }

    public String createUser(String email, String firstName, String lastName, String password) {
        String adminToken = getAdminToken();
        String usersUrl = serverUrl + "/admin/realms/" + realm + "/users";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        Map<String, Object> credential = Map.of(
                "type", "password",
                "value", password,
                "temporary", false
        );

        Map<String, Object> userRepresentation = Map.of(
                "username", email,
                "email", email,
                "firstName", firstName,
                "lastName", lastName,
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(credential)
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userRepresentation, headers);

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity(usersUrl, request, Void.class);

            // UUID extrait du header Location retourné par Keycloak (201 Created)
            URI location = response.getHeaders().getLocation();
            String[] parts = location.getPath().split("/");
            String keycloakId = parts[parts.length - 1];

            log.info("User created in Keycloak with id: {}", keycloakId);
            return keycloakId;

        } catch (HttpClientErrorException.Conflict e) {
            throw new RuntimeException("Email already exists in Keycloak: " + email);
        } catch (Exception e) {
            log.error("Failed to create user in Keycloak", e);
            throw new RuntimeException("Failed to create user in Keycloak: " + e.getMessage());
        }
    }

    /**
     * Rollback — supprime le user dans Keycloak si la DB échoue.
     *
     * Problème classique des transactions distribuées :
     * Keycloak et PostgreSQL sont deux systèmes séparés.
     * @Transactional ne couvre que PostgreSQL — pas Keycloak.
     *
     * Scénario sans rollback :
     *   1. createUser() → Keycloak OK
     *   2. userProfileRepository.save() → DB crash
     *   3. Résultat : user dans Keycloak mais pas en DB = incohérence
     *
     * Avec ce rollback :
     *   1. createUser() → Keycloak OK
     *   2. DB crash → on appelle deleteKeycloakUser()
     *   3. Résultat : ni dans Keycloak ni en DB = cohérence
     */
    public void deleteKeycloakUser(String keycloakId) {
        try {
            String adminToken = getAdminToken();
            String deleteUrl = serverUrl + "/admin/realms/" + realm + "/users/" + keycloakId;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(adminToken);

            restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
            log.info("Keycloak user deleted (rollback): {}", keycloakId);
        } catch (Exception e) {
            // On log l'erreur mais on ne throw pas — le rollback est best-effort
            log.error("Failed to rollback Keycloak user {}: {}", keycloakId, e.getMessage());
        }
    }


    public Map<String, Object> getTokenForUser(String email, String password) {
        String tokenUrl = serverUrl + "/realms/" + realm + "/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "password");
        body.add("client_id", clientId);
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