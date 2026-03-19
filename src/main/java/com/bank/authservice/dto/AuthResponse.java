package com.bank.authservice.dto;

import lombok.Builder;
import lombok.Data;

/**
 * AuthResponse — réponse du endpoint /api/auth/register.
 *
 * On ne retourne PLUS de token JWT ici.
 * Le client obtient son token directement depuis Keycloak après inscription.
 */
@Data
@Builder
public class AuthResponse {

    private String message;      // "Registration successful. You can now login via Keycloak."
    private String email;        // email de l'utilisateur inscrit
    private String keycloakId;   // UUID Keycloak (= "sub" dans le JWT futur)
    // Token Keycloak — valide 5 minutes (300s par défaut)
    private String accessToken;

    // Refresh token — permet de renouveler l'access token sans re-login
    private String refreshToken;

    // Durée de validité en secondes (ex: 300)
    private Integer expiresIn;

}