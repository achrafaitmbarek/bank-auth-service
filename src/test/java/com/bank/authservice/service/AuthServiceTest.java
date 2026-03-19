package com.bank.authservice.service;

import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)     // Active Mockito pour cette classe de test
class AuthServiceTest {

    // @Mock crée un faux objet — pas de vraie BDD, pas de vrai JWT
    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    // @InjectMocks crée AuthService et injecte tous les @Mock dedans
    @InjectMocks
    private AuthService authService;

    // Objets réutilisables dans tous les tests
    private User mockUser;
    private RegisterRequest registerRequest;
    private LoginRequest loginRequest;

    @BeforeEach     // S'exécute AVANT chaque test — réinitialise l'état
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("achraf@bank.com")
                .password("$2a$10$hashedPassword")
                .role(Role.USER)
                .build();

        registerRequest = new RegisterRequest();
        registerRequest.setEmail("achraf@bank.com");
        registerRequest.setPassword("secret123");

        loginRequest = new LoginRequest();
        loginRequest.setEmail("achraf@bank.com");
        loginRequest.setPassword("secret123");
    }

    // ─────────────────────────────────────────
    // REGISTER TESTS
    // ─────────────────────────────────────────

    @Test
    void register_ShouldReturnAuthResponse_WhenEmailIsNew() {
        // GIVEN — on définit le comportement des mocks
        when(userRepository.existsByEmail("achraf@bank.com"))
                .thenReturn(false);         // email pas encore en BDD
        when(passwordEncoder.encode("secret123"))
                .thenReturn("$2a$10$hashedPassword");
        when(userRepository.save(any(User.class)))
                .thenReturn(mockUser);
        when(jwtService.generateToken(anyString(), anyString()))
                .thenReturn("fake.jwt.token");

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("fake-refresh-token")
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        when(jwtService.generateRefreshToken(any(User.class)))
                .thenReturn(mockRefreshToken);

        // WHEN — on exécute la méthode à tester
        AuthResponse response = authService.register(registerRequest);

        // THEN — on vérifie le résultat
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("achraf@bank.com");
        assertThat(response.getToken()).isEqualTo("fake.jwt.token");
        assertThat(response.getRole()).isEqualTo("USER");

        // Vérifie que save() a bien été appelé UNE fois
        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_ShouldThrow409_WhenEmailAlreadyExists() {
        // GIVEN
        when(userRepository.existsByEmail("achraf@bank.com"))
                .thenReturn(true);          // email déjà en BDD

        // WHEN + THEN — on vérifie que l'exception est bien lancée
        assertThatThrownBy(() -> authService.register(registerRequest))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Email already registered");

        // Vérifie que save() n'a JAMAIS été appelé
        verify(userRepository, never()).save(any(User.class));
    }

    // ─────────────────────────────────────────
    // LOGIN TESTS
    // ─────────────────────────────────────────

    @Test
    void login_ShouldReturnAuthResponse_WhenCredentialsAreValid() {
        // GIVEN
        when(userRepository.findByEmail("achraf@bank.com"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedPassword"))
                .thenReturn(true);          // mot de passe correct
        when(jwtService.generateToken(anyString(), anyString()))
                .thenReturn("fake.jwt.token");

        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("fake-refresh-token")
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();
        when(jwtService.generateRefreshToken(any(User.class)))
                .thenReturn(mockRefreshToken);

        // WHEN
        AuthResponse response = authService.login(loginRequest);

        // THEN
        assertThat(response).isNotNull();
        assertThat(response.getEmail()).isEqualTo("achraf@bank.com");
        assertThat(response.getToken()).isEqualTo("fake.jwt.token");
    }

    @Test
    void login_ShouldThrow401_WhenEmailNotFound() {
        // GIVEN
        when(userRepository.findByEmail("achraf@bank.com"))
                .thenReturn(Optional.empty());  // email inexistant

        // WHEN + THEN
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_ShouldThrow401_WhenPasswordIsWrong() {
        // GIVEN
        when(userRepository.findByEmail("achraf@bank.com"))
                .thenReturn(Optional.of(mockUser));
        when(passwordEncoder.matches("secret123", "$2a$10$hashedPassword"))
                .thenReturn(false);     // mauvais mot de passe

        // WHEN + THEN
        assertThatThrownBy(() -> authService.login(loginRequest))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid credentials");

        // Vérifie que generateToken n'a JAMAIS été appelé
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    // ─────────────────────────────────────────
    // LOGOUT TESTS
    // ─────────────────────────────────────────

    @Test
    void logout_ShouldRevokeToken_WhenTokenIsValid() {
        // GIVEN
        RefreshToken mockRefreshToken = RefreshToken.builder()
                .token("valid-refresh-token")
                .user(mockUser)
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        when(refreshTokenRepository.findByToken("valid-refresh-token"))
                .thenReturn(Optional.of(mockRefreshToken));

        // WHEN
        authService.logout("valid-refresh-token");

        // THEN — vérifie que le token est bien révoqué
        assertThat(mockRefreshToken.isRevoked()).isTrue();
        verify(refreshTokenRepository, times(1)).save(mockRefreshToken);
    }

    @Test
    void logout_ShouldThrow401_WhenTokenNotFound() {
        // GIVEN
        when(refreshTokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        // WHEN + THEN
        assertThatThrownBy(() -> authService.logout("invalid-token"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid refresh token");
    }
}