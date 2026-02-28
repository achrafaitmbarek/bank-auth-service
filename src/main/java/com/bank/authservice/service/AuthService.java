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
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ApiException("Email already registered", HttpStatus.CONFLICT);
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .build();

        userRepository.save(user);

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
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ApiException(
                        "Invalid credentials", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new ApiException("Invalid credentials", HttpStatus.UNAUTHORIZED);
        }

        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        RefreshToken refreshToken = jwtService.generateRefreshToken(user);

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }

    // Génère un nouvel Access Token depuis un Refresh Token valide
    public AuthResponse refreshToken(String refreshTokenValue) {

        // Cherche le refresh token en BDD
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(refreshTokenValue)
                .orElseThrow(() -> new ApiException(
                        "Invalid refresh token", HttpStatus.UNAUTHORIZED));

        // Vérifie qu'il n'est pas révoqué
        if (refreshToken.isRevoked()) {
            throw new ApiException("Refresh token has been revoked", HttpStatus.UNAUTHORIZED);
        }

        // Vérifie qu'il n'est pas expiré
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ApiException("Refresh token has expired", HttpStatus.UNAUTHORIZED);
        }

        // Génère un nouvel Access Token
        User user = refreshToken.getUser();
        String newToken = jwtService.generateToken(user.getEmail(), user.getRole().name());

        return AuthResponse.builder()
                .token(newToken)
                .refreshToken(refreshToken.getToken())
                // On retourne le même refresh token — pas besoin d'en générer un nouveau
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}