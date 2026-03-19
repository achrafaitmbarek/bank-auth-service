package com.bank.authservice.controller;

import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Register endpoint — Login goes directly through Keycloak")
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/register
     * Crée le user dans Keycloak + sauvegarde un profil dans notre DB.
     *
     * Note : le login n'est plus géré ici.
     * Le client appelle directement Keycloak :
     * POST http://localhost:8180/realms/bank-app/protocol/openid-connect/token
     */
    @Operation(summary = "Register a new user (creates account in Keycloak)")
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}