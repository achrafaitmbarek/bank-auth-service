package com.bank.authservice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
@Tag(name = "User", description = "Protected user endpoints")
@SecurityRequirement(name = "Bearer Authentication")

public class UserController {


    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user")
    public ResponseEntity<Map<String, String>> getCurrentUser() {

        // SecurityContextHolder contient ce que notre filtre a mis
        // c'est à dire l'email extrait du JWT
        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return ResponseEntity.ok(Map.of(
                "email", email,
                "message", "You are authenticated"
        ));
    }
}