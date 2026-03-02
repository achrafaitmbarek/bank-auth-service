package com.bank.authservice.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // ConcurrentHashMap = Map thread-safe
    // Clé = IP de l'appelant
    // Valeur = son Bucket (compteur de tokens)
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    // Crée ou récupère le bucket pour une IP donnée
    private Bucket getBucket(String ip) {
        return buckets.computeIfAbsent(ip, key -> {
            // Bandwidth = règle de limite
            // 5 requêtes maximum
            // rechargées toutes les 60 secondes
            Bandwidth limit = Bandwidth.builder()
                    .capacity(5)
                    .refillIntervally(5, Duration.ofMinutes(1))
                    .build();
            return Bucket.builder()
                    .addLimit(limit)
                    .build();
        });
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // On applique le rate limiting uniquement sur les routes d'auth
        // Les routes publiques comme /swagger-ui ne sont pas limitées
        if (!request.getRequestURI().startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Récupère l'IP du client
        String ip = request.getRemoteAddr();

        // Récupère le bucket de cette IP
        Bucket bucket = getBucket(ip);

        // Tente de consommer 1 token
        if (bucket.tryConsume(1)) {
            // Token disponible → la requête passe
            filterChain.doFilter(request, response);
        } else {
            // Plus de tokens → trop de requêtes
            log.warn("Rate limit exceeded for IP: {}", ip);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("""
                    {
                        "status": 429,
                        "message": "Too many requests. Please try again later."
                    }
                    """);
        }
    }
}