package com.bank.authservice.repository;

import com.bank.authservice.domain.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileRepository extends JpaRepository<UserProfile, String> {

    boolean existsByEmail(String email);
}