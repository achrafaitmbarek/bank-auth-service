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

        // Sauvegarde profil dans notre DB
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
        log.info("Kafka event sent for: {}", request.getEmail());

        // Auto-login : récupère le token Keycloak immédiatement après inscription
        // L'user n'a pas besoin de faire un second appel pour se connecter
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
    }
}
