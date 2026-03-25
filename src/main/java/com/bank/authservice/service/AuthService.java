package com.bank.authservice.service;

import com.bank.authservice.domain.UserProfile;
import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.event.UserRegisteredEvent;
import com.bank.authservice.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserProfileRepository userProfileRepository;
    private final KeycloakAdminService keycloakAdminService;
    private final KafkaProducerService kafkaProducerService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Vérification rapide en base locale
        if (userProfileRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered: " + request.getEmail());
        }

        // Création dans Keycloak → retourne le keycloakId (UUID)
        String keycloakId = keycloakAdminService.createUser(
                request.getEmail(),
                request.getFirstName(),
                request.getLastName(),
                request.getPassword()
        );

        try {
            // Sauvegarde profil dans notre DB
            // Si ça échoue → le catch supprime le user dans Keycloak (rollback)
            UserProfile profile = UserProfile.builder()
                    .id(keycloakId)
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .createdAt(LocalDateTime.now())
                    .build();

            userProfileRepository.save(profile);
            log.info("User profile saved for keycloakId: {}", keycloakId);

            // Event Kafka → notification-service
            UserRegisteredEvent event = UserRegisteredEvent.builder()
                    .email(request.getEmail())
                    .role("USER")
                    .build();
            kafkaProducerService.publishUserRegistered(event);

            // Auto-login : token Keycloak retourné directement
            Map<String, Object> tokenResponse = keycloakAdminService.getTokenForUser(
                    request.getEmail(),
                    request.getPassword()
            );

            return AuthResponse.builder()
                    .message("Registration successful.")
                    .email(request.getEmail())
                    .keycloakId(keycloakId)
                    .accessToken((String) tokenResponse.get("access_token"))
                    .refreshToken((String) tokenResponse.get("refresh_token"))
                    .expiresIn((Integer) tokenResponse.get("expires_in"))
                    .build();

        } catch (Exception e) {
            // ROLLBACK DISTRIBUÉ :
            // @Transactional rollback automatiquement PostgreSQL
            // mais Keycloak est un système externe → on doit le rollback manuellement
            log.error("Registration failed for {}, rolling back Keycloak user {}",
                    request.getEmail(), keycloakId);
            keycloakAdminService.deleteKeycloakUser(keycloakId);
            throw new RuntimeException("Registration failed: " + e.getMessage());
        }
    }
}