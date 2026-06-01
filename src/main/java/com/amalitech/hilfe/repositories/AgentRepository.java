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
            LEFT JOIN FETCH a.user
            WHERE a.status = true
            """,
            countQuery = """
            SELECT COUNT(a) FROM Agent a
            WHERE a.status = true
            """)
    Page<Agent> findAllActiveWithUser(Pageable pageable);

    @Query(
            value = """
                    SELECT DISTINCT a FROM Agent a
                    LEFT JOIN FETCH a.user
                    WHERE EXISTS (
                        SELECT 1 FROM AgentGroupMember m
                        JOIN m.agentGroup g
                        WHERE m.agentId = a.id
                          AND g.departmentId = :departmentId
                          AND g.status = true
                    )
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
                    """
    )
    Page<Agent> findByDepartmentIdWithUser(@Param("departmentId") String departmentId, Pageable pageable);

    @Query("""
            SELECT a FROM Agent a
            LEFT JOIN FETCH a.user
            WHERE a.id = :agentId
            """)
    Optional<Agent> findByIdWithUser(@Param("agentId") String agentId);

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
