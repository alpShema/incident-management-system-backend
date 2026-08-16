package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private static final String DEPARTMENT_ALREADY_EXISTS_MESSAGE = "A department with this name already exists. Please choose a different name.";

    private final DepartmentRepository departmentRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final IncidentCategoryRepository categoryRepository;
    private final IncidentTypeRepository typeRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<DepartmentResponse> listDepartments(String query, Boolean status, Pageable pageable) {
        String queryPattern = null;
        if (query != null && !query.isBlank()) {
            queryPattern = "%" + query.toLowerCase()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_") + "%";
        }
        return departmentRepository.search(queryPattern, status, pageable)
                .map(this::toResponse);
    }

    public DepartmentResponse getDepartment(String id) {
        return toResponse(findDepartmentByIdOrThrow(id));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        if (departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException(DEPARTMENT_ALREADY_EXISTS_MESSAGE, 409);
        }

        Department department = Department.builder()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .status(true)
                .build();
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartment(String id, DepartmentRequest request) {
        String name = request.name() == null ? null : request.name().trim();
        String description = request.description() == null ? null : request.description().trim();

        Department department = findDepartmentByIdOrThrow(id);
        if (name != null && !department.getName().equalsIgnoreCase(name)
                && departmentRepository.existsByNameIgnoreCase(name)) {
            throw new ArmsAuthException(DEPARTMENT_ALREADY_EXISTS_MESSAGE, 409);
        }

        if (name != null) {
            department.setName(name);
        }
        if (description != null) {
            department.setDescription(description);
        }
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartmentStatus(String id, Boolean status) {
        Department department = findDepartmentByIdOrThrow(id);

        if (status.equals(department.getStatus())) {
            throw new ArmsAuthException(
                    Boolean.TRUE.equals(status)
                            ? "Department is already active"
                            : "Department is already inactive",
                    409);
        }

        department.setStatus(status);
        Department saved = departmentRepository.save(department);

        if (Boolean.FALSE.equals(status)) {
            deactivateLinkedRecords(id);
        }

        return toResponse(saved);
    }

    private void deactivateLinkedRecords(String departmentId) {
        List<IncidentCategory> categories = categoryRepository.findByDepartmentIdAndStatus(departmentId, true);
        categories.forEach(category -> {
            category.setStatus(false);
            deactivateTopics(category.getId());
        });
        if (!categories.isEmpty()) {
            categoryRepository.saveAll(categories);
        }

        List<AgentGroup> agentGroups = agentGroupRepository.findByDepartmentIdAndStatus(departmentId, true);
        agentGroups.forEach(agentGroup -> agentGroup.setStatus(false));
        if (!agentGroups.isEmpty()) {
            agentGroupRepository.saveAll(agentGroups);
        }
    }

    private void deactivateTopics(String categoryId) {
        List<IncidentType> topics = typeRepository.findByCategoryId(categoryId).stream()
                .filter(topic -> Boolean.TRUE.equals(topic.getStatus()))
                .toList();
        if (!topics.isEmpty()) {
            topics.forEach(topic -> topic.setStatus(false));
            typeRepository.saveAll(topics);
        }
    }

    public List<IncidentCategoryResponse> listCategories(String departmentId) {
        findDepartmentByIdOrThrow(departmentId);
        return categoryRepository.findByDepartmentIdAndStatusWithDepartment(departmentId, true).stream()
                .map(IncidentCategoryResponse::from)
                .toList();
    }

    @Transactional
    public IncidentCategoryResponse addCategory(String departmentId, String categoryId) {
        findActiveDepartment(departmentId);
        IncidentCategory category = findCategory(categoryId);
        category.setDepartmentId(departmentId);
        categoryRepository.save(category);
        entityManager.flush();
        entityManager.clear();
        return IncidentCategoryResponse.from(categoryRepository.findByIdWithDepartment(categoryId).orElseThrow());
    }

    @Transactional
    public IncidentCategoryResponse removeCategory(String departmentId, String categoryId) {
        findActiveDepartment(departmentId);
        IncidentCategory category = findCategory(categoryId);
        if (!departmentId.equals(category.getDepartmentId())) {
            throw new ArmsAuthException("Incident category is not linked to this department", 400);
        }
        category.setDepartmentId(null);
        return IncidentCategoryResponse.from(categoryRepository.save(category));
    }

    private DepartmentResponse toResponse(Department department) {
        long categoryCount = categoryRepository.findByDepartmentIdAndStatus(department.getId(), true).size();
        return DepartmentResponse.from(department, categoryCount);
    }

    private Department findActiveDepartment(String id) {
        return departmentRepository.findById(id)
                .filter(department -> Boolean.TRUE.equals(department.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Department not found", 404));
    }

    private Department findDepartmentByIdOrThrow(String id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Department not found", 404));
    }

    private IncidentCategory findCategory(String categoryId) {
        return categoryRepository.findById(categoryId)
                .filter(category -> Boolean.TRUE.equals(category.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
    }
}
