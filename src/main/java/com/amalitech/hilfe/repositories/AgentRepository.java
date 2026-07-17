package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Agent;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
    Optional<Agent> findByUserId(String userId);

    @Query(value = """
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            LEFT JOIN FETCH u.location l
            WHERE u.roleCode IN ('AGENT', 'ADMIN_AGENT')
            AND (:available IS NULL OR a.status = :available)
            AND (:locationId IS NULL OR u.locationId = :locationId)
            AND (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Agent a
            LEFT JOIN a.user u
            LEFT JOIN u.location l
            WHERE u.roleCode IN ('AGENT', 'ADMIN_AGENT')
            AND (:available IS NULL OR a.status = :available)
            AND (:locationId IS NULL OR u.locationId = :locationId)
            AND (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """)
    Page<Agent> findAllWithUserAndQuery(
            @Param("queryPattern") String queryPattern,
            @Param("available") Boolean available,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query(value = """
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            LEFT JOIN FETCH u.location l
            WHERE a.status = true
            AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
            AND (:available IS NULL OR a.status = :available)
            AND (:locationId IS NULL OR u.locationId = :locationId)
            AND (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Agent a
            WHERE a.status = true
            AND (:available IS NULL OR a.status = :available)
            AND EXISTS (
                SELECT 1 FROM User u
                LEFT JOIN u.location l
                WHERE u.id = a.userId
                AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
                AND (:locationId IS NULL OR u.locationId = :locationId)
                AND (:queryPattern IS NULL OR (
                    LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                    OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                    OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
                ))
            )
            """)
    Page<Agent> findAllActiveWithUserAndQuery(
            @Param("queryPattern") String queryPattern,
            @Param("available") Boolean available,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT DISTINCT a FROM Agent a
                    LEFT JOIN FETCH a.user u
                    LEFT JOIN FETCH u.location l
                    WHERE EXISTS (
                        SELECT 1 FROM AgentGroupMember m
                        JOIN m.agentGroup g
                        WHERE m.agentId = a.id
                          AND g.departmentId = :departmentId
                          AND g.status = true
                    )
                    AND a.status = true
                    AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
                    AND (:available IS NULL OR a.status = :available)
                    AND (:locationId IS NULL OR u.locationId = :locationId)
                    AND (:queryPattern IS NULL OR (
                        LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                        OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
                    ))
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT a) FROM Agent a
                    WHERE EXISTS (
                        SELECT 1 FROM AgentGroupMember m
                        JOIN m.agentGroup g
                        WHERE m.agentId = a.id
                          AND g.departmentId = :departmentId
                          AND g.status = true
                    )
                    AND a.status = true
                    AND (:available IS NULL OR a.status = :available)
                    AND EXISTS (
                        SELECT 1 FROM User u
                        LEFT JOIN u.location l
                        WHERE u.id = a.userId
                        AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
                        AND (:locationId IS NULL OR u.locationId = :locationId)
                        AND (:queryPattern IS NULL OR (
                            LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                            OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                            OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
                        ))
                    )
                    """
    )
    Page<Agent> findByDepartmentIdWithUserAndQuery(
            @Param("departmentId") String departmentId,
            @Param("queryPattern") String queryPattern,
            @Param("available") Boolean available,
            @Param("locationId") String locationId,
            Pageable pageable
    );

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            LEFT JOIN FETCH u.location
            WHERE a.id = :agentId
            """)
    Optional<Agent> findByIdWithUser(@Param("agentId") String agentId);

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            LEFT JOIN FETCH u.location
            WHERE a.userId = :userId
            """)
    Optional<Agent> findByUserIdWithUser(@Param("userId") String userId);

    long countByAgentGroupId(String agentGroupId);

    @Query("SELECT a.userId FROM Agent a WHERE a.id = :agentId")
    Optional<String> findUserIdByAgentId(@Param("agentId") String agentId);

    List<Agent> findByAgentGroupId(String agentGroupId);

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user
            WHERE a.agentGroupId = :agentGroupId
            """)
    List<Agent> findByAgentGroupIdWithUser(@Param("agentGroupId") String agentGroupId);

    @Query("SELECT a.agentGroupId FROM Agent a WHERE a.id = :agentId")
    Optional<String> findAgentGroupIdByAgentId(@Param("agentId") String agentId);

    @Query("""
            SELECT CASE WHEN COUNT(ag) > 0 THEN true ELSE false END
            FROM AgentGroup ag
            WHERE ag.status = true
            AND (
                ag.id = :agentGroupId
                OR ag.id IN (
                    SELECT m.agentGroupId FROM AgentGroupMember m WHERE m.agentId = :agentId
                )
            )
            """)
    boolean hasActiveGroup(@Param("agentGroupId") String agentGroupId, @Param("agentId") String agentId);

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user
            WHERE a.agentGroupId = (
                SELECT a2.agentGroupId FROM Agent a2 WHERE a2.id = :agentId
            )
            """)
    List<Agent> findAgentsInSameDepartment(@Param("agentId") String agentId);

    @Query("""
            SELECT a FROM Agent a
            WHERE a.agentGroupId = :agentGroupId
            AND a.status = true
            """)
    List<Agent> findAvailableByAgentGroupId(@Param("agentGroupId") String agentGroupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            WHERE a.status = true
            AND u.status = true
            AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
            AND EXISTS (
                SELECT 1 FROM AgentGroupMember m
                JOIN m.agentGroup g
                WHERE m.agentId = a.id AND m.agentGroupId = :agentGroupId
                AND g.status = true
            )
            AND u.locationId = :locationId
            ORDER BY a.lastAssignedAt ASC NULLS FIRST
            """)
    List<Agent> findAvailableByAgentGroupIdAndLocation(
            @Param("agentGroupId") String agentGroupId,
            @Param("locationId") String locationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            WHERE a.status = true
            AND u.status = true
            AND u.roleCode IN ('AGENT', 'ADMIN_AGENT')
            AND EXISTS (
                SELECT 1 FROM AgentGroupMember m
                JOIN m.agentGroup g
                WHERE m.agentId = a.id AND m.agentGroupId = :agentGroupId
                AND g.status = true
            )
            ORDER BY a.lastAssignedAt ASC NULLS FIRST
            """)
    List<Agent> findAvailableByAgentGroupIdViaMembership(@Param("agentGroupId") String agentGroupId);
}
