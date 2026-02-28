package com.bank.authservice.service;

import com.bank.authservice.domain.RefreshToken;
import com.bank.authservice.domain.User;
import com.bank.authservice.repository.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;

    // Access Token — courte durée
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Refresh Token — longue durée, stocké en BDD
    public RefreshToken generateRefreshToken(User user) {

        // Révoque tous les anciens refresh tokens du user
        refreshTokenRepository.revokeAllUserTokens(user.getId());
        // Un user = un seul refresh token valide à la fois
        // Si l'utilisateur se reconnecte, l'ancien token est révoqué

        RefreshToken refreshToken = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                // UUID = identifiant unique aléatoire
                // "a3f5c2d1-4b6e-..." — impossible à deviner
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(refreshExpiration / 1000))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            return !extractAllClaims(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = java.util.HexFormat.of().parseHex(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}