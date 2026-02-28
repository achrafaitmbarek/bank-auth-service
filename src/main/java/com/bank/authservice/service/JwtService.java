package com.bank.authservice.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.expiration}")
    private long expiration;

    // Génère un token — déjà existant
    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // Extrait l'email depuis le token
    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
        // Le subject c'est l'email qu'on a mis dans generateToken()
    }

    // Extrait le rôle depuis le token
    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
        // "role" c'est le claim custom qu'on a mis dans generateToken()
    }

    // Vérifie si le token est valide et non expiré
    public boolean isTokenValid(String token) {
        try {
            return !extractAllClaims(token)
                    .getExpiration()
                    .before(new Date());
            // before(new Date()) = est-ce que la date d'expiration est passée ?
            // Si oui → token expiré → false
        } catch (Exception e) {
            return false;
            // Si le token est malformé ou la signature invalide
            // Jwts.parser() lance une exception — on retourne false
        }
    }

    // Extrait tous les claims du token — méthode privée utilitaire
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())  // Vérifie la signature
                .build()
                .parseSignedClaims(token)
                .getPayload();               // Retourne le contenu du token
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = java.util.HexFormat.of().parseHex(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}