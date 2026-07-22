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
import com.amalitech.hilfe.repositories.specifications.IncidentCategorySpecifications;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentCategoryService {
    private static final String CATEGORY_NOT_FOUND = "Incident category not found";
    private static final String TOPIC_NOT_FOUND = "Incident topic not found";
    private static final String TOPIC_NOT_FOUND_AFTER_UPDATE = "Incident topic not found after update";
    private static final String TOPIC_ALREADY_EXISTS_MESSAGE = "A topic with this name already exists. Please choose a different name.";
    private static final String CATEGORY_ALREADY_EXISTS_MESSAGE = "An incident category with this name already exists. Please choose a different name.";
    private static final String SORT_NAME = "name";
    private static final String SORT_STATUS = "status";
    private static final String SORT_CREATED_AT = "createdAt";
    private static final String SORT_UPDATED_AT = "updatedAt";
    private static final String SORT_CATEGORY_NAME = "category.name";
    private static final String SORT_AGENT_GROUP_NAME = "agentGroup.name";

    private static final Map<String, String> TOPIC_SORT_FIELD_ALIASES = Map.of(
            "category", SORT_CATEGORY_NAME,
            "agentGroup", SORT_AGENT_GROUP_NAME
    );

    private static final Set<String> TOPIC_ALLOWED_SORT_FIELDS = Set.of(
            SORT_NAME, SORT_STATUS, SORT_CREATED_AT, SORT_UPDATED_AT, SORT_CATEGORY_NAME, SORT_AGENT_GROUP_NAME
    );

    private final IncidentCategoryRepository categoryRepository;
    private final IncidentTypeRepository typeRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final DepartmentRepository departmentRepository;
    private final IncidentRepository incidentRepository;
    private final EntityManager entityManager;

    public Page<IncidentCategoryResponse> listCategories(Boolean status, Boolean hasActiveTopics, String query, Pageable pageable) {
        String queryPattern = buildQueryPattern(query);

        Specification<IncidentCategory> spec = Specification
                .where(IncidentCategorySpecifications.withDepartment())
                .and(resolveStatusFilter(status, hasActiveTopics))
                .and(activeTopicsFilter(hasActiveTopics))
                .and(IncidentCategorySpecifications.matchesQuery(queryPattern));

        return categoryRepository.findAll(spec, pageable)
                .map(IncidentCategoryResponse::from);
    }

    public Page<IncidentCategoryResponse> listAllCategories(Boolean status, Boolean hasActiveTopics, String query, Pageable pageable) {
        String queryPattern = buildQueryPattern(query);

        Specification<IncidentCategory> spec = Specification
                .where(IncidentCategorySpecifications.withDepartment())
                .and(categoryStatusFilter(status))
                .and(activeTopicsFilter(hasActiveTopics))
                .and(IncidentCategorySpecifications.matchesQuery(queryPattern));

        return categoryRepository.findAll(spec, pageable)
                .map(IncidentCategoryResponse::from);
    }

    private Specification<IncidentCategory> categoryStatusFilter(Boolean status) {
        if (status == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Boolean.TRUE.equals(status)
                ? IncidentCategorySpecifications.isActiveCategory()
                : Specification.not(IncidentCategorySpecifications.isActiveCategory());
    }

    private Specification<IncidentCategory> activeTopicsFilter(Boolean hasActiveTopics) {
        if (hasActiveTopics == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return Boolean.TRUE.equals(hasActiveTopics)
                ? IncidentCategorySpecifications.hasActiveTopics()
                : Specification.not(IncidentCategorySpecifications.hasActiveTopics());
    }

    private Specification<IncidentCategory> resolveStatusFilter(Boolean status, Boolean hasActiveTopics) {
        if (status != null) {
            return categoryStatusFilter(status);
        }
        if (hasActiveTopics != null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return categoryStatusFilter(Boolean.TRUE);
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
            throw new ArmsAuthException(CATEGORY_ALREADY_EXISTS_MESSAGE, 409);
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
                throw new ArmsAuthException(CATEGORY_ALREADY_EXISTS_MESSAGE, 409);
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
        category.setStatus(status);
        categoryRepository.save(category);
        return IncidentCategoryResponse.from(category);
    }

    public List<IncidentTopicResponse> listTopicsByCategory(String categoryId, Boolean status) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException(CATEGORY_NOT_FOUND, 404);
        }
        return typeRepository.findByCategoryIdWithAgentAndStatus(categoryId, status).stream()
                .map(IncidentTopicResponse::from)
                .toList();
    }

    @Transactional
    public Page<IncidentTopicListResponse> listTopics(
            String categoryId,
            String departmentId,
            String agentGroupId,
            Boolean status,
            String query,
            Pageable pageable
    ) {
        String queryPattern = buildQueryPattern(query);
        return typeRepository.findAllTopicsFiltered(
                        blankToNull(categoryId),
                        blankToNull(departmentId),
                        blankToNull(agentGroupId),
                        status,
                        queryPattern,
                        ensureTopicSorted(pageable))
                .map(IncidentTopicListResponse::from);
    }

    private Pageable ensureTopicSorted(Pageable pageable) {
        if (pageable.getSort().isSorted()) {
            Sort translated = translateTopicSort(pageable.getSort());
            return pageable.isUnpaged()
                    ? Pageable.unpaged(translated)
                    : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
        }
        if (pageable.isUnpaged()) return Pageable.unpaged(Sort.by(Sort.Direction.ASC, SORT_NAME));
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(Sort.Direction.ASC, SORT_NAME));
    }

    private Sort translateTopicSort(Sort sort) {
        List<Sort.Order> orders = sort.stream()
                .map(o -> TOPIC_SORT_FIELD_ALIASES.containsKey(o.getProperty())
                        ? o.withProperty(TOPIC_SORT_FIELD_ALIASES.get(o.getProperty()))
                        : o)
                .filter(o -> TOPIC_ALLOWED_SORT_FIELDS.contains(o.getProperty()))
                .toList();
        if (orders.isEmpty()) {
            return Sort.by(Sort.Direction.ASC, SORT_NAME);
        }
        return Sort.by(orders);
    }

    @Transactional
    public IncidentTopicResponse createTopic(String categoryId, String creatorUserId, CreateTopicRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        IncidentCategory category = findActiveCategory(categoryId);
        if (typeRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException(TOPIC_ALREADY_EXISTS_MESSAGE, 409);
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
                throw new ArmsAuthException(TOPIC_ALREADY_EXISTS_MESSAGE, 409);
            }
            topic.setName(name);
        }
        if (description != null && !description.isBlank()) {
            topic.setDescription(description);
        }
        applyAgentGroupChange(topic, request, category);
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

        String effectiveCategoryId = (request.categoryId() != null && !request.categoryId().isBlank())
                ? request.categoryId()
                : topic.getCategoryId();
        IncidentCategory category = categoryRepository.findById(effectiveCategoryId)
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));

        if (StringUtils.hasText(name)) {
            boolean isDuplicate = !topic.getName().equalsIgnoreCase(name)
                    && typeRepository.existsByNameIgnoreCase(name);
            if (isDuplicate) {
                throw new ArmsAuthException(TOPIC_ALREADY_EXISTS_MESSAGE, 409);
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
        applyAgentGroupChange(topic, request, category);
        if (request.visibleToGroup() != null) {
            topic.setVisibleToGroup(request.visibleToGroup());
        }

        IncidentType saved = typeRepository.save(topic);
        entityManager.flush();
        entityManager.clear();
        return IncidentTopicResponse.from(typeRepository.findByIdWithDetails(saved.getId())
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND_AFTER_UPDATE, 500)));
    }

    private void applyAgentGroupChange(IncidentType topic, UpdateTopicRequest request, IncidentCategory category) {
        boolean hasNewGroup = request.agentGroupId() != null && !request.agentGroupId().isBlank();
        if (Boolean.TRUE.equals(request.removeAgentGroup())) {
            if (hasNewGroup) {
                throw new ArmsAuthException("Cannot remove and reassign the agent group in the same request", 400);
            }
            topic.setAgentGroupId(null);
            return;
        }
        if (hasNewGroup) {
            AgentGroup assignedGroup = resolveAssignableAgentGroup(request.agentGroupId(), category);
            topic.setAgentGroupId(assignedGroup.getId());
        }
    }

    @Transactional
    public IncidentTopicResponse updateTopicStatus(String topicId, Boolean status) {
        IncidentType topic = typeRepository.findById(topicId)
                .orElseThrow(() -> new ArmsAuthException(TOPIC_NOT_FOUND, 404));
        if (status != null && status.equals(topic.getStatus())) {
            throw new ArmsAuthException(
                    Boolean.TRUE.equals(status) ? "Incident topic is already active"
                                                : "Incident topic is already inactive", 409);
        }
        topic.setStatus(status);
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
            throw new ArmsAuthException("This topic cannot be deleted because it is used by existing incidents.", 409);
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
                .filter(category -> Boolean.TRUE.equals(category.getStatus()))
                .orElseThrow(() -> new ArmsAuthException(CATEGORY_NOT_FOUND, 404));
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
