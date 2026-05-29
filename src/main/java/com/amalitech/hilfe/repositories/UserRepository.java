package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.dto.UserRoleSummaryResponse;
import com.amalitech.hilfe.models.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Owner: Lawson
 * Depends on: JPA entity mappings for User.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    @Query(
            value = """
                    SELECT new com.amalitech.hilfe.dto.UserRoleSummaryResponse(
                        user.id,
                        user.email,
                        user.fullName,
                        user.profileImg,
                        user.roleCode,
                        user.status,
                        loc.name,
                        (SELECT COUNT(i) FROM Incident i WHERE i.userId = user.id),
                        (SELECT COUNT(i) FROM Incident i WHERE i.assignedToId = agent.id)
                    )
                    FROM User user
                    LEFT JOIN user.location loc
                    LEFT JOIN user.agent agent
                    """,
            countQuery = """
                    SELECT COUNT(user)
                    FROM User user
                    """
    )
    Page<UserRoleSummaryResponse> findUserRoleSummaries(Pageable pageable);

    @Query(
            value = """
                    SELECT new com.amalitech.hilfe.dto.UserRoleSummaryResponse(
                        user.id,
                        user.email,
                        user.fullName,
                        user.profileImg,
                        user.roleCode,
                        user.status,
                        loc.name,
                        (SELECT COUNT(i) FROM Incident i WHERE i.userId = user.id),
                        (SELECT COUNT(i) FROM Incident i WHERE i.assignedToId = agent.id)
                    )
                    FROM User user
                    LEFT JOIN user.location loc
                    LEFT JOIN user.agent agent
                    WHERE (:queryPattern IS NULL OR (
                        LOWER(user.fullName) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(user.email) LIKE :queryPattern ESCAPE '!'))
                    AND (:roleCode IS NULL OR user.roleCode = :roleCode)
                    AND (:locationId IS NULL OR user.locationId = :locationId)
                    AND (:status IS NULL OR user.status = :status)
                    """,
            countQuery = """
                    SELECT COUNT(user)
                    FROM User user
                    WHERE (:queryPattern IS NULL OR (
                        LOWER(user.fullName) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(user.email) LIKE :queryPattern ESCAPE '!'))
                    AND (:roleCode IS NULL OR user.roleCode = :roleCode)
                    AND (:locationId IS NULL OR user.locationId = :locationId)
                    AND (:status IS NULL OR user.status = :status)
                    """
    )
    Page<UserRoleSummaryResponse> findUserRoleSummariesUnified(
            @Param("queryPattern") String queryPattern,
            @Param("roleCode") String roleCode,
            @Param("locationId") String locationId,
            @Param("status") Boolean status,
            Pageable pageable
    );

    @Query("""
            SELECT user
            FROM User user
            LEFT JOIN FETCH user.admin
            LEFT JOIN FETCH user.agent
            WHERE user.id = :id
            """)
    Optional<User> findAuthUserById(@Param("id") String id);

    /**
     * Atomic upsert by ARMS user id (which is the local PK).
     * INSERT on first login with a default CLIENT role, UPDATE profile fields on every subsequent login.
     * Status and role_code are intentionally excluded from the UPDATE so admin changes survive re-login.
     * clearAutomatically = true flushes the JPA cache so the following findById hits the DB.
     */
    @Modifying
    @Query("UPDATE User u SET u.tokenVersion = u.tokenVersion + 1 WHERE u.id = :userId")
    void incrementTokenVersion(@Param("userId") String userId);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            INSERT INTO "User" (id, email, full_name, contact, profile_img, position, location_id, role_code, status, created_at, updated_at)
            VALUES (:id, :email, :fullName, :contact, :profileImg, :position, :locationId, 'CLIENT', true, NOW(), NOW())
            ON CONFLICT (id)
            DO UPDATE SET
                email       = EXCLUDED.email,
                full_name   = EXCLUDED.full_name,
                contact     = EXCLUDED.contact,
                profile_img = EXCLUDED.profile_img,
                position    = EXCLUDED.position,
                location_id = COALESCE(EXCLUDED.location_id, "User".location_id),
                updated_at  = NOW()
            """, nativeQuery = true)
    void upsert(
            @Param("id") String id,
            @Param("email") String email,
            @Param("fullName") String fullName,
            @Param("contact") String contact,
            @Param("profileImg") String profileImg,
            @Param("position") String position,
            @Param("locationId") String locationId
    );
}
