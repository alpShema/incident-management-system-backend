package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.AgentResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.repositories.AgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;

    public Page<AgentResponse> listAgents(Pageable pageable) {
        return agentRepository.findAllActiveWithUser(pageable).map(AgentResponse::from);
    }

    public AgentResponse updateAvailability(String userId, boolean available) {
        Agent agent = agentRepository.findByUserId(userId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        agent.setStatus(available);
        Agent saved = agentRepository.save(agent);
        return AgentResponse.from(saved);
    }
}
