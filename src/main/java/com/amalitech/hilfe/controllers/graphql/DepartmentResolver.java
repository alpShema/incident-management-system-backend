package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.CreateDepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.DepartmentService;
import com.amalitech.hilfe.services.JwtTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DepartmentResolver {

    private final DepartmentService departmentService;

    @QueryMapping
    @PreAuthorize("hasAuthority('department.read')")
    public PageResponse<DepartmentResponse> departments(
            @Argument String query,
            @Argument Boolean status,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                departmentService.listDepartments(query, status, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('department.read')")
    public DepartmentResponse department(@Argument String id) {
        return departmentService.getDepartment(id);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('department.read')")
    public List<IncidentCategoryResponse> departmentCategories(@Argument String departmentId) {
        return departmentService.listCategories(departmentId);
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('department.read')")
    public List<DepartmentResponse> departmentsHeadedBy(@Argument String userId) {
        return departmentService.listDepartmentsHeadedBy(userId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.create')")
    public DepartmentResponse createDepartment(
            @Valid @Argument CreateDepartmentRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Department created successfully");
        return departmentService.createDepartment(principal.userId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.update')")
    public DepartmentResponse updateDepartment(
            @Argument String id,
            @Valid @Argument DepartmentRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Department updated successfully");
        return departmentService.updateDepartment(principal.userId(), id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.delete')")
    public DepartmentResponse updateDepartmentStatus(@Argument String id, @Valid @Argument UpdateDepartmentStatusInput input) {
        GraphQlResponseMessage.set("Department status updated successfully");
        return departmentService.updateDepartmentStatus(id, input.status());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.update')")
    public IncidentCategoryResponse linkCategoryToDepartment(@Argument String departmentId, @Argument String categoryId) {
        GraphQlResponseMessage.set("Department category added successfully");
        return departmentService.addCategory(departmentId, categoryId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.update')")
    public IncidentCategoryResponse unlinkCategoryFromDepartment(@Argument String departmentId, @Argument String categoryId) {
        GraphQlResponseMessage.set("Department category removed successfully");
        return departmentService.removeCategory(departmentId, categoryId);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.update')")
    public DepartmentResponse removeDepartmentHead(
            @Argument String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Department head removed successfully");
        return departmentService.removeDepartmentHead(principal.userId(), id);
    }

    public record UpdateDepartmentStatusInput(Boolean status) {}
}
