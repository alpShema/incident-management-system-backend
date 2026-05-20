package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentCategoryService {
    private final IncidentCategoryRepository categoryRepository;
    private final IncidentTypeRepository typeRepository;
    private final AgentRepository agentRepository;
    private final IncidentRepository incidentRepository;

    public List<IncidentCategoryResponse> listCategories() {
        return categoryRepository.findByStatus("active").stream()
                .map(IncidentCategoryResponse::from)
                .toList();
    }

    @Transactional
    public IncidentCategoryResponse createCategory(IncidentCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Incident category with this name already exists", 409);
        }
        IncidentCategory category = IncidentCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .build();
        return IncidentCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public IncidentCategoryResponse updateCategory(String id, IncidentCategoryRequest request) {
        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
        category.setName(request.name());
        category.setDescription(request.description());
        return IncidentCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(String id) {
        deactivateCategory(id);
    }

    public List<IncidentTopicResponse> listTopicsByCategory(String categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        return typeRepository.findByCategoryIdWithAgent(categoryId).stream()
                .map(IncidentTopicResponse::from)
                .toList();
    }

    @Transactional
    public IncidentTopicResponse createTopic(String categoryId, String creatorUserId, CreateTopicRequest request) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        if (typeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("A topic with this name already exists", 409);
        }
        Agent assignedAgent = resolveTopicAgent(creatorUserId, request.agentId());
        IncidentType topic = IncidentType.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .categoryId(categoryId)
                .adminId(creatorUserId)
                .agentId(assignedAgent.getId())
                .visibleToGroup(request.visibleToGroup())
                .build();
        IncidentType saved = typeRepository.save(topic);
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId()).orElse(saved));
    }

    @Transactional
    public IncidentTopicResponse updateTopic(String categoryId, String topicId, UpdateTopicRequest request) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException("Incident topic not found", 404));
        if (!categoryId.equals(topic.getCategoryId())) {
            throw new ArmsAuthException("Incident topic not found in category", 404);
        }

        if (request.name() != null && !request.name().isBlank()) {
            if (!topic.getName().equalsIgnoreCase(request.name())
                    && typeRepository.existsByNameIgnoreCase(request.name())) {
                throw new ArmsAuthException("A topic with this name already exists", 409);
            }
            topic.setName(request.name());
        }
        if (request.description() != null && !request.description().isBlank()) {
            topic.setDescription(request.description());
        }
        if (request.agentId() != null && !request.agentId().isBlank()) {
            topic.setAgentId(resolveDepartmentBackedAgent(request.agentId()).getId());
        }
        if (request.visibleToGroup() != null) {
            topic.setVisibleToGroup(request.visibleToGroup());
        }

        IncidentType saved = typeRepository.save(topic);
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(topicId).orElse(saved));
    }

    @Transactional
    public void deleteTopic(String categoryId, String topicId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException("Incident topic not found", 404));
        if (!categoryId.equals(topic.getCategoryId())) {
            throw new ArmsAuthException("Incident topic not found in category", 404);
        }
        if (incidentRepository.existsByIncidentTypeId(topicId)) {
            throw new ArmsAuthException("Incident topic is referenced by incidents", 409);
        }
        typeRepository.delete(topic);
    }

    @Transactional
    public IncidentCategoryResponse deactivateCategory(String id) {
        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
        category.setStatus("inactive");
        categoryRepository.save(category);
        return IncidentCategoryResponse.from(category);
    }

    private Agent resolveTopicAgent(String creatorUserId, String requestedAgentId) {
        if (requestedAgentId != null && !requestedAgentId.isBlank()) {
            return resolveDepartmentBackedAgent(requestedAgentId);
        }
        Agent creatorAgent = agentRepository.findByUserId(creatorUserId)
                .orElseThrow(() -> new ArmsAuthException("Authenticated user is not linked to an agent record", 403));
        return validateDepartmentBackedAgent(creatorAgent);
    }

    private Agent resolveDepartmentBackedAgent(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new ArmsAuthException("Agent not found", 404));
        return validateDepartmentBackedAgent(agent);
    }

    private Agent validateDepartmentBackedAgent(Agent agent) {
        if (agent.getAgentGroupId() == null || agent.getAgentGroupId().isBlank()) {
            throw new ArmsAuthException("Agent must belong to a department", 400);
        }
        return agent;
    }
}
