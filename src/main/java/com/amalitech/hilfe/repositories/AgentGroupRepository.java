package com.amalitech.hilfe.repositories;

import com.amalitech.hilfe.models.AgentGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentGroupRepository extends JpaRepository<AgentGroup, String> {
    boolean existsByNameIgnoreCase(String name);
    List<AgentGroup> findByStatus(Boolean status);
}
