package com.bank.authservice.config;

import com.bank.authservice.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    // OncePerRequestFilter garantit que ce filtre s'exécute
    // exactement UNE FOIS par requête — jamais deux fois

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Étape 1 — Récupère le header Authorization
        String authHeader = request.getHeader("Authorization");
        // Le client envoie : Authorization: Bearer eyJhbGci...

        // Étape 2 — Vérifie que le header existe et commence par "Bearer "
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            // Pas de token → on passe au filtre suivant sans authentifier
            // Spring Security décidera si la route nécessite un token ou non
            return;
        }

        // Étape 3 — Extrait le token (enlève "Bearer ")
        String token = authHeader.substring(7);
        // "Bearer eyJhbGci..." → "eyJhbGci..."

        // Étape 4 — Valide le token
        if (!jwtService.isTokenValid(token)) {
            filterChain.doFilter(request, response);
            return;
            // Token invalide ou expiré → pas d'authentification
        }

        // Étape 5 — Extrait les informations du token
        String email = jwtService.extractEmail(token);
        String role = jwtService.extractRole(token);

        // Étape 6 — Crée l'objet d'authentification Spring Security
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,    // principal — qui est l'utilisateur
                        null,     // credentials — null car JWT, pas de password ici
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))
                        // authorities — les rôles de l'utilisateur
                        // Spring Security attend le format "ROLE_USER" ou "ROLE_ADMIN"
                );

        // Étape 7 — Dit à Spring Security que cette requête est authentifiée
        SecurityContextHolder.getContext().setAuthentication(authentication);
        // SecurityContext = la mémoire de Spring Security pour cette requête
        // Après cette ligne Spring Security sait qui fait la requête

        // Étape 8 — Continue vers le Controller
        filterChain.doFilter(request, response);
    }
}