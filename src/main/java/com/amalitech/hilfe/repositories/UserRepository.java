package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * Owner: Lawson
 * Depends on: JPA entity mappings for User.
 */
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    /**
     * Atomic upsert by ARMS user id (which is the local PK).
     * INSERT on first login, UPDATE profile fields on every subsequent login.
     * Status is intentionally excluded from the UPDATE so an admin deactivation survives re-login.
     * clearAutomatically = true flushes the JPA cache so the following findById hits the DB.
     */
    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO "User" (id, email, full_name, profile_img, status, created_at, updated_at)
            VALUES (:id, :email, :fullName, :profileImg, true, NOW(), NOW())
            ON CONFLICT (id)
            DO UPDATE SET
                email       = EXCLUDED.email,
                full_name   = EXCLUDED.full_name,
                profile_img = EXCLUDED.profile_img,
                updated_at  = NOW()
            """, nativeQuery = true)
    void upsert(
            @Param("id")         String id,
            @Param("email")      String email,
            @Param("fullName")   String fullName,
            @Param("profileImg") String profileImg
    );
}
