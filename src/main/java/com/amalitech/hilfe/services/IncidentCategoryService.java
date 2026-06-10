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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentCategoryService {
    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_INACTIVE = "inactive";
    private static final String CATEGORY_NOT_FOUND = "Incident category not found";
    private static final String TOPIC_NOT_FOUND = "Incident topic not found";
    private static final String TOPIC_NOT_FOUND_AFTER_UPDATE = "Incident topic not found after update";

    private final IncidentCategoryRepository categoryRepository;
    private final IncidentTypeRepository typeRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final DepartmentRepository departmentRepository;
    private final IncidentRepository incidentRepository;
    private final EntityManager entityManager;

    public Page<IncidentCategoryResponse> listCategories(String status, String query, Pageable pageable) {
        String resolvedStatus = (status == null || status.isBlank()) ? STATUS_ACTIVE : status.toLowerCase();
        String queryPattern = buildQueryPattern(query);
        return categoryRepository.findByStatusWithDepartmentAndQueryPaged(resolvedStatus, queryPattern, pageable)
                .map(IncidentCategoryResponse::from);
    }

    public Page<IncidentCategoryResponse> listAllCategories(String state, String query, Pageable pageable) {
        String resolvedStatus = resolveStateFilter(state);
        String queryPattern = buildQueryPattern(query);
        return categoryRepository.findAllWithDepartmentAndQueryPaged(resolvedStatus, queryPattern, pageable)
                .map(IncidentCategoryResponse::from);
    }

    public Page<IncidentCategoryResponse> searchCategories(String query, Pageable pageable) {
        String queryPattern = (query == null || query.isBlank()) ? null
                : "%" + query.toLowerCase().replace("%", "\\%").replace("_", "\\_") + "%";
        return categoryRepository.searchCategories(queryPattern, pageable)
                .map(IncidentCategoryResponse::from);
    }

    @Transactional
    public IncidentCategoryResponse createCategory(IncidentCategoryRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException("Incident category with this name already exists", 409);
        }
        validateDepartment(request.departmentId());
        IncidentCategory category = IncidentCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .departmentId(request.departmentId())
                .build();
        IncidentCategory saved = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();
        return IncidentCategoryResponse.from(categoryRepository.findByIdWithDepartment(saved.getId()).orElse(saved));
    }

    @Transactional
    public IncidentCategoryResponse updateCategory(String id, UpdateIncidentCategoryRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));

        if (name != null && !name.isBlank()) {
            if (!category.getName().equalsIgnoreCase(name)
                    && categoryRepository.existsByNameIgnoreCase(name)) {
                throw new ArmsAuthException("Incident category with this name already exists", 409);
            }
            category.setName(name);
        }
        if (description != null) {
            category.setDescription(description);
        }
        if (request.departmentId() != null && !request.departmentId().isBlank()) {
            validateDepartment(request.departmentId());
            category.setDepartmentId(request.departmentId());
        }

        IncidentCategory saved = categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();
        return IncidentCategoryResponse.from(categoryRepository.findByIdWithDepartment(saved.getId()).orElse(saved));
    }

    @Transactional
    public IncidentCategoryResponse updateCategoryStatus(String id, Boolean status) {
        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));
        category.setStatus(Boolean.TRUE.equals(status) ? STATUS_ACTIVE : STATUS_INACTIVE);
        categoryRepository.save(category);
        return IncidentCategoryResponse.from(category);
    }

    public List<IncidentTopicResponse> listTopicsByCategory(String categoryId, String status) {
        String resolvedStatus = normalizeTopicStatus(status);
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException(CATEGORY_NOT_FOUND, 404);
        }
        return typeRepository.findByCategoryIdWithAgentAndStatus(categoryId, resolvedStatus).stream()
                .map(IncidentTopicResponse::from)
                .toList();
    }

    @Transactional
    public Page<IncidentTopicListResponse> listTopics(
            String categoryId,
            String departmentId,
            String agentGroupId,
            String status,
            String query,
            Pageable pageable
    ) {
        String resolvedStatus = normalizeTopicStatus(status);
        String queryPattern = buildQueryPattern(query);
        return typeRepository.findAllTopicsFiltered(
                        blankToNull(categoryId),
                        blankToNull(departmentId),
                        blankToNull(agentGroupId),
                        resolvedStatus,
                        queryPattern,
                        pageable)
                .map(IncidentTopicListResponse::from);
    }

    @Transactional
    public IncidentTopicResponse createTopic(String categoryId, String creatorUserId, CreateTopicRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        IncidentCategory category = findActiveCategory(categoryId);
        if (typeRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException("A topic with this name already exists", 409);
        }
        AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId(), category);
        IncidentType topic = IncidentType.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .categoryId(categoryId)
                .adminId(creatorUserId)
                .agentGroupId(assignedGroup.getId())
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
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        IncidentCategory category = findActiveCategory(categoryId);
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND, 404));
        if (!categoryId.equals(topic.getCategoryId())) {
            throw new ArmsAuthException("Incident topic not found in category", 404);
        }

        if (name != null && !name.isBlank()) {
            if (!topic.getName().equalsIgnoreCase(name)
                    && typeRepository.existsByNameIgnoreCase(name)) {
                throw new ArmsAuthException("A topic with this name already exists", 409);
            }
            topic.setName(name);
        }
        if (description != null && !description.isBlank()) {
            topic.setDescription(description);
        }
        if (request.agentGroupId() != null && !request.agentGroupId().isBlank()) {
            AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId(), category);
            topic.setAgentGroupId(assignedGroup.getId());
        }
        if (request.visibleToGroup() != null) {
            topic.setVisibleToGroup(request.visibleToGroup());
        }

        IncidentType saved = typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId())
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_AFTER_UPDATE, 500)));
    }

    @Transactional
    public IncidentTopicResponse updateTopicById(String topicId, UpdateTopicRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND, 404));

        // Resolve the effective category: use the incoming categoryId if provided,
        // otherwise fall back to the topic's existing category. This ensures that when
        // both categoryId and agentGroupId are updated together, the department check
        // runs against the new category rather than the stale one.
        String effectiveCategoryId = (request.categoryId() != null && !request.categoryId().isBlank())
                ? request.categoryId()
                : topic.getCategoryId();
        IncidentCategory category = categoryRepository.findById(effectiveCategoryId)
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));

        if (name != null && !name.isBlank()) {
            if (!topic.getName().equalsIgnoreCase(name)
                    && typeRepository.existsByNameIgnoreCase(name)) {
                throw new ArmsAuthException("A topic with this name already exists", 409);
            }
            topic.setName(name);
        }
        if (description != null && !description.isBlank()) {
            topic.setDescription(description);
        }
        if (request.categoryId() != null && !request.categoryId().isBlank()) {
            findActiveCategory(request.categoryId());
            topic.setCategoryId(request.categoryId());
        }
        if (request.agentGroupId() != null && !request.agentGroupId().isBlank()) {
            AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId(), category);
            topic.setAgentGroupId(assignedGroup.getId());
        }
        if (request.visibleToGroup() != null) {
            topic.setVisibleToGroup(request.visibleToGroup());
        }

        IncidentType saved = typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId())
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_AFTER_UPDATE, 500)));
    }

    @Transactional
    public IncidentTopicResponse updateTopicStatus(String topicId, Boolean status) {
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND, 404));
        String target = Boolean.TRUE.equals(status) ? STATUS_ACTIVE : STATUS_INACTIVE;
        if (target.equalsIgnoreCase(topic.getStatus())) {
            throw new ArmsAuthException(
                    Boolean.TRUE.equals(status) ? "Incident topic is already active"
                                                : "Incident topic is already inactive", 409);
        }
        topic.setStatus(target);
        typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_AFTER_UPDATE, 500)));
    }

    @Transactional
    public void deleteTopic(String categoryId, String topicId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException(CATEGORY_NOT_FOUND, 404);
        }
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND, 404));
        if (!categoryId.equals(topic.getCategoryId())) {
            throw new ArmsAuthException("Incident topic not found in category", 404);
        }
        if (incidentRepository.existsByIncidentTypeId(topicId)) {
            throw new ArmsAuthException("Incident topic is referenced by incidents", 409);
        }
        typeRepository.delete(topic);
    }

    private void validateDepartment(String departmentId) {
        departmentRepository.findById(departmentId)
                .filter(department -> Boolean.TRUE.equals(department.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Department not found", 404));
    }

    private AgentGroup resolveAssignableAgentGroup(String agentGroupId, IncidentCategory category) {
        AgentGroup agentGroup = agentGroupRepository.findById(agentGroupId)
                .filter(group -> Boolean.TRUE.equals(group.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Agent group not found", 404));
        if (agentGroup.getDepartmentId() == null || !agentGroup.getDepartmentId().equals(category.getDepartmentId())) {
            throw new ArmsAuthException("Agent group must belong to the same department as the incident category", 400);
        }
        return agentGroup;
    }

    private IncidentCategory findActiveCategory(String categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> STATUS_ACTIVE.equalsIgnoreCase(category.getStatus()))
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));
    }

    private String normalizeTopicStatus(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return null;
        }
        String value = status.trim().toLowerCase();
        if (!STATUS_ACTIVE.equals(value) && !STATUS_INACTIVE.equals(value)) {
            throw new ArmsAuthException("Invalid status. Allowed values are active, inactive, or all", 400);
        }
        return value;
    }

    private String resolveStateFilter(String state) {
        if (state == null || state.isBlank() || "all".equalsIgnoreCase(state)) return null;
        String lower = state.toLowerCase();
        if (!STATUS_ACTIVE.equals(lower) && !STATUS_INACTIVE.equals(lower)) {
            throw new ArmsAuthException("Invalid state filter. Allowed values are active, inactive, or all", 400);
        }
        return lower;
    }

    private String buildQueryPattern(String query) {
        if (query == null || query.isBlank()) return null;
        return "%" + query.toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
