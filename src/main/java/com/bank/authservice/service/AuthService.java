package com.bank.authservice.service;

import com.bank.authservice.config.JwtService;
import com.bank.authservice.domain.Role;
import com.bank.authservice.domain.User;
import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.exception.ApiException;
import com.bank.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor   // Lombok génère un constructeur avec tous les attributs final
// C'est la façon recommandée de faire l'injection de dépendances
public class AuthService {

    private final UserRepository userRepository;   // final = obligatoire dans le constructeur
    private final PasswordEncoder passwordEncoder; // pour hasher les mots de passe
    private final JwtService jwtService;           // pour générer les tokens JWT

    public AuthResponse register(RegisterRequest request) {

        // Étape 1 — Vérifier si l'email existe déjà
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Email already registered", HttpStatus.CONFLICT);
        }

        // Étape 2 — Construire l'utilisateur
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                // encode() transforme "secret123" en "$2a$10$xyz..."
                // BCrypt — algorithme standard en fintech, irréversible
                .role(Role.USER)
                // Toujours USER — jamais le client ne choisit son rôle
                .build();

        // Étape 3 — Sauvegarder en BDD
        userRepository.save(user);
        // Hibernate génère : INSERT INTO users (email, password, role) VALUES (?, ?, ?)

        // Étape 4 — Générer le token JWT
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        // Étape 5 — Retourner la réponse
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}