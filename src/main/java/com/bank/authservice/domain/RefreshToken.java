package com.bank.authservice.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;
    // Le token lui-même — unique en BDD

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    // Chaque refresh token appartient à un User
    // ManyToOne = plusieurs tokens peuvent appartenir au même user
    // FetchType.LAZY = on ne charge pas le User depuis la BDD
    // sauf si on en a besoin — optimisation performance

    @Column(nullable = false)
    private LocalDateTime expiresAt;
    // Date d'expiration stockée en BDD
    // On peut vérifier côté serveur sans décoder le token

    @Column(nullable = false)
    private boolean revoked = false;
    // Permet de révoquer un token sans le supprimer
    // Utile pour l'audit en banque — on garde une trace
}