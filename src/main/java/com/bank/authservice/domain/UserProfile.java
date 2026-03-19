package com.bank.authservice.domain;  // ← domain pas entity

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * UserProfile — profil métier stocké dans notre DB.
 *
 * On ne stocke PLUS le mot de passe ici.
 * Keycloak gère l'authentification.
 *
 * id = keycloakId (UUID du "sub" dans le JWT Keycloak)
 * → permet de lier les données bancaires futures à un user Keycloak
 */
@Entity
@Table(name = "user_profile")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "id", length = 36)
    private String id;          // UUID Keycloak

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}