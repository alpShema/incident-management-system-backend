package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
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
            WHERE (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Agent a
            LEFT JOIN a.user u
            LEFT JOIN u.location l
            WHERE (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """)
    Page<Agent> findAllWithUserAndQuery(@Param("queryPattern") String queryPattern, Pageable pageable);

    @Query(value = """
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            LEFT JOIN FETCH u.location l
            WHERE a.status = true
            AND (:queryPattern IS NULL OR (
                LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
            ))
            """,
            countQuery = """
            SELECT COUNT(a) FROM Agent a
            WHERE a.status = true
            AND (:queryPattern IS NULL OR EXISTS (
                SELECT 1 FROM User u
                LEFT JOIN u.location l
                WHERE u.id = a.userId
                AND (
                    LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                    OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                    OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
                )
            ))
            """)
    Page<Agent> findAllActiveWithUserAndQuery(@Param("queryPattern") String queryPattern, Pageable pageable);

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
                    AND (:queryPattern IS NULL OR EXISTS (
                        SELECT 1 FROM User u
                        LEFT JOIN u.location l
                        WHERE u.id = a.userId
                        AND (
                            LOWER(u.fullName) LIKE :queryPattern ESCAPE '!'
                            OR LOWER(u.email) LIKE :queryPattern ESCAPE '!'
                            OR LOWER(l.name) LIKE :queryPattern ESCAPE '!'
                        )
                    ))
                    """
    )
    Page<Agent> findByDepartmentIdWithUserAndQuery(
            @Param("departmentId") String departmentId,
            @Param("queryPattern") String queryPattern,
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

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            WHERE a.status = true
            AND EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = a.id AND m.agentGroupId = :agentGroupId
            )
            AND u.locationId = :locationId
            """)
    List<Agent> findAvailableByAgentGroupIdAndLocation(
            @Param("agentGroupId") String agentGroupId,
            @Param("locationId") String locationId
    );

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user u
            WHERE a.status = true
            AND EXISTS (
                SELECT 1 FROM AgentGroupMember m
                WHERE m.agentId = a.id AND m.agentGroupId = :agentGroupId
            )
            """)
    List<Agent> findAvailableByAgentGroupIdViaMembership(@Param("agentGroupId") String agentGroupId);
}
