package com.bank.authservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder                  // Pour construire la réponse proprement dans le Service
@AllArgsConstructor
public class AuthResponse {

    private String token;   // Le JWT token que le client va stocker
    private String email;   // Confirmation de l'email enregistré
    private String role;    // Le rôle assigné ("USER" ou "ADMIN")
}