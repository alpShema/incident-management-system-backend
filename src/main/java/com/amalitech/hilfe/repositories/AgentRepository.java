package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
    Optional<Agent> findByUserId(String userId);

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
}
