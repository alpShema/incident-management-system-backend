package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import jakarta.persistence.EntityManager;
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
    private final AgentGroupRepository agentGroupRepository;
    private final DepartmentRepository departmentRepository;
    private final IncidentRepository incidentRepository;
    private final EntityManager entityManager;

    public List<IncidentCategoryResponse> listCategories() {
        return categoryRepository.findByStatusWithDepartment("active").stream()
                .map(IncidentCategoryResponse::from)
                .toList();
    }

    @Transactional
    public IncidentCategoryResponse createCategory(IncidentCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Incident category with this name already exists", 409);
        }
        validateDepartment(request.departmentId());
        IncidentCategory category = IncidentCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .departmentId(request.departmentId())
                .build();
        IncidentCategory saved = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();
        return IncidentCategoryResponse.from(categoryRepository.findByIdWithDepartment(saved.getId()).orElse(saved));
    }

    @Transactional
    public IncidentCategoryResponse updateCategory(String id, IncidentCategoryRequest request) {
        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
        validateDepartment(request.departmentId());
        category.setName(request.name());
        category.setDescription(request.description());
        category.setDepartmentId(request.departmentId());
        IncidentCategory saved = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();
        return IncidentCategoryResponse.from(categoryRepository.findByIdWithDepartment(saved.getId()).orElse(saved));
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
        AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId());
        IncidentType topic = IncidentType.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .categoryId(categoryId)
                .adminId(creatorUserId)
                .agentGroupId(assignedGroup.getId())
                .agentId(assignedGroup.getPrimaryAgentId())
                .visibleToGroup(request.visibleToGroup())
                .build();
        IncidentType saved = typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId())
                .orElseThrow(() -> new ArmsAuthException("Incident topic not found after creation", 500)));
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
        if (request.agentGroupId() != null && !request.agentGroupId().isBlank()) {
            AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId());
            topic.setAgentGroupId(assignedGroup.getId());
            topic.setAgentId(assignedGroup.getPrimaryAgentId());
        }
        if (request.visibleToGroup() != null) {
            topic.setVisibleToGroup(request.visibleToGroup());
        }

        IncidentType saved = typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId())
                .orElseThrow(() -> new ArmsAuthException("Incident topic not found after update", 500)));
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

    private void validateDepartment(String departmentId) {
        if (!departmentRepository.existsById(departmentId)) {
            throw new ArmsAuthException("Department not found", 404);
        }
    }

    private AgentGroup resolveAssignableAgentGroup(String agentGroupId) {
        AgentGroup agentGroup = agentGroupRepository.findById(agentGroupId)
                .filter(group -> Boolean.TRUE.equals(group.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Agent group not found", 404));
        if (agentGroup.getPrimaryAgentId() == null || agentGroup.getPrimaryAgentId().isBlank()) {
            throw new ArmsAuthException("Agent group must have a primary agent", 400);
        }
        return agentGroup;
    }
}
