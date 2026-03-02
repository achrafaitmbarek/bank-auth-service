package com.bank.authservice.controller;

import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.LoginRequest;
import com.bank.authservice.dto.RefreshTokenRequest;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController                    // Dit à Spring : cette classe gère des requêtes HTTP REST
@RequestMapping("/api/auth")       // Préfixe de toutes les routes de ce controller
@RequiredArgsConstructor           // Injection de dépendances via constructeur
@Tag(name = "Authentication", description = "Register, Login, Refresh Token, Logout")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Register a new user")
    @PostMapping("/register")                          // POST /api/auth/register
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request
            // @RequestBody : transforme le JSON reçu en objet Java
            // @Valid      : déclenche les validations (@NotBlank, @Email, @Size...)
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
        // HTTP 201 Created — standard pour une création de ressource
        // En fintech on respecte strictement les codes HTTP
    }

    @Operation(summary = "Login with email and password")
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request){
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Refresh access token")
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request){
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Logout and revoke refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
        // HTTP 204 No Content — succès sans body
        // Standard pour les opérations qui ne retournent rien
    }
}