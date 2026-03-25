package com.bank.authservice.service;

import com.bank.authservice.domain.UserProfile;
import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.event.UserRegisteredEvent;
import com.bank.authservice.repository.UserProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// @ExtendWith(MockitoExtension.class) = active Mockito pour cette classe de test
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock = crée un faux objet — les méthodes ne font rien par défaut
    // On contrôle ce qu'elles retournent avec "when(...).thenReturn(...)"
    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private KeycloakAdminService keycloakAdminService;

    @Mock
    private KafkaProducerService kafkaProducerService;

    // @InjectMocks = crée le vrai AuthService avec les @Mock injectés dedans
    @InjectMocks
    private AuthService authService;

    // Données de test réutilisées dans tous les tests
    private RegisterRequest validRequest;

    // @BeforeEach = s'exécute avant chaque @Test
    @BeforeEach
    void setUp() {
        validRequest = new RegisterRequest();
        validRequest.setEmail("test@bank.com");
        validRequest.setPassword("Test1234!");
        validRequest.setFirstName("John");
        validRequest.setLastName("Doe");
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 1 : Register réussi → retourne les tokens
    // ─────────────────────────────────────────────────────────────
    @Test
    void register_shouldReturnTokens_whenUserIsNew() {
        // GIVEN — on configure les mocks pour simuler le comportement attendu
        // "quand on appelle existsByEmail → retourne false (email pas encore en DB)"
        when(userProfileRepository.existsByEmail("test@bank.com")).thenReturn(false);

        // "quand on appelle createUser → retourne un UUID Keycloak"
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("uuid-keycloak-123");

        // "quand on appelle save → retourne le profil sauvegardé"
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // "quand on appelle getTokenForUser → retourne un faux token Keycloak"
        when(keycloakAdminService.getTokenForUser(anyString(), anyString()))
                .thenReturn(Map.of(
                        "access_token", "fake-access-token",
                        "refresh_token", "fake-refresh-token",
                        "expires_in", 300
                ));

        // WHEN — on appelle la méthode qu'on teste
        AuthResponse response = authService.register(validRequest);

        // THEN — on vérifie que le résultat est correct
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("test@bank.com");
        assertThat(response.getKeycloakId()).isEqualTo("uuid-keycloak-123");
        assertThat(response.getAccessToken()).isEqualTo("fake-access-token");
        assertThat(response.getExpiresIn()).isEqualTo(300);

        // Vérifie que save() a bien été appelé 1 fois
        verify(userProfileRepository, times(1)).save(any(UserProfile.class));

        // Vérifie que Kafka a bien reçu l'event
        verify(kafkaProducerService, times(1)).publishUserRegistered(any(UserRegisteredEvent.class));
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 2 : Email déjà utilisé → exception
    // ─────────────────────────────────────────────────────────────
    @Test
    void register_shouldThrowException_whenEmailAlreadyExists() {
        // GIVEN — l'email existe déjà en DB
        when(userProfileRepository.existsByEmail("test@bank.com")).thenReturn(true);

        // WHEN + THEN — on s'attend à une RuntimeException
        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Email already registered");

        // Keycloak ne doit pas être appelé si l'email existe déjà
        verify(keycloakAdminService, never()).createUser(anyString(), anyString(), anyString(), anyString());
    }

    // ─────────────────────────────────────────────────────────────
    // TEST 3 : DB échoue → rollback Keycloak
    // ─────────────────────────────────────────────────────────────
    @Test
    void register_shouldRollbackKeycloak_whenDatabaseFails() {
        // GIVEN
        when(userProfileRepository.existsByEmail("test@bank.com")).thenReturn(false);
        when(keycloakAdminService.createUser(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("uuid-keycloak-123");

        // La DB plante !
        when(userProfileRepository.save(any(UserProfile.class)))
                .thenThrow(new RuntimeException("Database connection lost"));

        // WHEN + THEN — exception remontée
        assertThatThrownBy(() -> authService.register(validRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Registration failed");

        // VÉRIFICATION CLÉ : deleteKeycloakUser doit être appelé pour le rollback
        // C'est ce qui montre qu'on gère les transactions distribuées
        verify(keycloakAdminService, times(1)).deleteKeycloakUser("uuid-keycloak-123");
    }
}