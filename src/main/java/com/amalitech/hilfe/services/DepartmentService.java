package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
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
    private final DepartmentRepository departmentRepository;
    private final AgentGroupRepository agentGroupRepository;
    private final IncidentCategoryRepository categoryRepository;

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
        return toResponse(findActiveDepartment(id));
    }

    @Transactional
    public DepartmentResponse createDepartment(DepartmentRequest request) {
        if (departmentRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Department with this name already exists", 409);
        }

        Department department = Department.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .status(true)
                .build();
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartment(String id, DepartmentRequest request) {
        Department department = findDepartmentByIdOrThrow(id);
        if (!department.getName().equalsIgnoreCase(request.name())
                && departmentRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Department with this name already exists", 409);
        }

        department.setName(request.name());
        department.setDescription(request.description());
        return toResponse(departmentRepository.save(department));
    }

    @Transactional
    public DepartmentResponse updateDepartmentStatus(String id, Boolean status) {
        Department department = findDepartmentByIdOrThrow(id);

        if (Boolean.valueOf(status).equals(department.getStatus())) {
            throw new ArmsAuthException(
                    Boolean.TRUE.equals(status)
                            ? "Department is already active"
                            : "Department is already inactive",
                    409);
        }

        if (Boolean.FALSE.equals(status)) {
            if (categoryRepository.existsByDepartmentId(id)) {
                throw new ArmsAuthException("Department has assigned incident categories", 409);
            }
            if (agentGroupRepository.existsByDepartmentIdAndStatus(id, true)) {
                throw new ArmsAuthException("Department has assigned agent groups", 409);
            }
        }

        department.setStatus(status);
        return toResponse(departmentRepository.save(department));
    }

    public List<IncidentCategoryResponse> listCategories(String departmentId) {
        findActiveDepartment(departmentId);
        return categoryRepository.findByDepartmentIdAndStatusWithDepartment(departmentId, "active").stream()
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
        long categoryCount = categoryRepository.findByDepartmentIdAndStatus(department.getId(), "active").size();
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
                .filter(category -> "active".equalsIgnoreCase(category.getStatus()))
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
    }
}
