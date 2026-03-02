package com.bank.authservice.service;

import com.bank.authservice.config.JwtAuthFilter;
import com.bank.authservice.domain.RefreshToken;
import com.bank.authservice.domain.Role;
import com.bank.authservice.domain.User;
import com.bank.authservice.dto.AuthResponse;
import com.bank.authservice.dto.LoginRequest;
import com.bank.authservice.dto.RegisterRequest;
import com.bank.authservice.exception.ApiException;
import com.bank.authservice.repository.RefreshTokenRepository;
import com.bank.authservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        log.info("Register attempt for email: {}", request.getEmail());
        // {} = placeholder — jamais de concaténation dans les logs
        // "email: " + email est dangereux → "email: {}" est safe et performant
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Register failed - email already exists: {}", request.getEmail());
            throw new ApiException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);
        log.info("User registered successfully: {}", user.getEmail());

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                        log.warn("Login failed - email not found: {}", request.getEmail());
                        return new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED);});

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed - wrong password for email: {}", request.getEmail());
            throw new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = jwtService.generateRefreshToken(user);
        log.info("Login successful for email: {}", user.getEmail());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public AuthResponse refreshToken(String refreshTokenValue) {
        log.info("Refresh token attempt");

        // Cherche le refresh token en BDD
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() -> {
                    log.warn("Refresh failed - token not found");
                    return new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
                });


        // Vérifie qu'il n'est pas révoqué
        if (refreshToken.isRevoked()) {
            log.warn("Refresh failed - token already revoked for user: {}",
                    refreshToken.getUser().getEmail());
            throw new ApiException("Refresh token has been revoked", HttpStatus.UNAUTHORIZED);
        }

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            log.warn("Refresh failed - token expired for user: {}",
                    refreshToken.getUser().getEmail());
            throw new ApiException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        // Génère un nouvel Access Token
        User user = refreshToken.getUser();
        String newToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        log.info("Access token refreshed for user: {}", user.getEmail());

        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(refreshToken.getToken())
                // On retourne le même refresh token — pas besoin d'en générer un nouveau
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    @Transactional
    public void logout(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() -> {
                    log.warn("Logout failed - token not found");
                    return new ApiException("Invalid refresh token", HttpStatus.UNAUTHORIZED);
                });

        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
        log.info("User logged out: {}", refreshToken.getUser().getEmail());
    }
}