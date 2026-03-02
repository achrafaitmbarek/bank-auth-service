package com.bank.authservice.repository;

import com.bank.authservice.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);
    // Cherche un refresh token par sa valeur

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId")
    void revokeAllUserTokens(Long userId);
    // Révoque tous les tokens d'un user d'un coup
    // Utile lors d'un changement de mot de passe ou d'une déconnexion globale
    // @Modifying = dit à JPA que c'est une requête d'écriture (UPDATE/DELETE)
}