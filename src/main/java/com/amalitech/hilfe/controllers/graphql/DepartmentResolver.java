package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.DepartmentRequest;
import com.amalitech.hilfe.dto.DepartmentResponse;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @MutationMapping
    @PreAuthorize("hasAuthority('department.create')")
    public DepartmentResponse createDepartment(@Valid @Argument DepartmentRequest input) {
        GraphQlResponseMessage.set("Department created successfully");
        return departmentService.createDepartment(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('department.update')")
    public DepartmentResponse updateDepartment(@Argument String id, @Valid @Argument DepartmentRequest input) {
        GraphQlResponseMessage.set("Department updated successfully");
        return departmentService.updateDepartment(id, input);
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

    public record UpdateDepartmentStatusInput(Boolean status) {}
}
