package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroupMember;
import com.amalitech.hilfe.models.AgentGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentGroupMemberRepository extends JpaRepository<AgentGroupMember, AgentGroupMemberId> {
    boolean existsByAgentIdAndAgentGroupId(String agentId, String agentGroupId);

    long countByAgentGroupId(String agentGroupId);

    void deleteByAgentIdAndAgentGroupId(String agentId, String agentGroupId);

    void deleteByAgentGroupId(String agentGroupId);

    void deleteByAgentId(String agentId);

    @Query("""
            SELECT m.agentGroupId FROM AgentGroupMember m
            WHERE m.agentId = :agentId
            """)
    List<String> findAgentGroupIdsByAgentId(@Param("agentId") String agentId);

    @Query("""
            SELECT m.agent FROM AgentGroupMember m
            LEFT JOIN FETCH m.agent.user u
            LEFT JOIN FETCH u.location
            WHERE m.agentGroupId = :agentGroupId
            """)
    List<Agent> findAgentsByAgentGroupIdWithUser(@Param("agentGroupId") String agentGroupId);
}
