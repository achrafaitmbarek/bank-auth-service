package com.bank.authservice.controller;

import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.LoginRequest;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController                    // Dit à Spring : cette classe gère des requêtes HTTP REST
@RequestMapping("/api/auth")       // Préfixe de toutes les routes de ce controller
@RequiredArgsConstructor           // Injection de dépendances via constructeur
public class AuthController {

    private final AuthService authService;

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

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request){
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}