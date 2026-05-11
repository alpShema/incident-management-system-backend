package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.Agent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {
    Optional<Agent> findByUserId(String userId);
}
