package com.bank.authservice.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.util.Date;


@Service   // Spring gère cette classe comme un composant injectable
public class JwtService {

    @Value("${jwt.secret}")
    private String secretKey;
    // @Value injecte la valeur depuis application.yml
    // Si la clé change, tu changes application.yml, pas le code

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)               // L'identifiant principal du token
                .claim("role", role)          // Données supplémentaires dans le token
                .issuedAt(new Date())         // Date de création
                .expiration(new Date(System.currentTimeMillis() + expiration))  // Date d'expiration
                .signWith(getSigningKey())     // Signature avec notre clé secrète
                .compact();                   // Génère le token String final
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = java.util.HexFormat.of().parseHex(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
        // Convertit notre clé hexadécimale en clé cryptographique
        // HMAC-SHA = algorithme de signature standard pour JWT
    }
}