package com.bank.authservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getCurrentUser() {

        // SecurityContextHolder contient ce que notre filtre a mis
        // c'est à dire l'email extrait du JWT
        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();  // getName() retourne le principal = l'email

        return ResponseEntity.ok(Map.of(
                "email", email,
                "message", "You are authenticated"
        ));
    }
}