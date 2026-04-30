package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Owner: Lawson
 * Depends on: JPA entity mappings for User.
 */
public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
}
