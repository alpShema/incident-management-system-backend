package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.*;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Departments", description = "Manage internal departments and the incident categories under them")
@RestController
@RequestMapping("/departments")
@RequiredArgsConstructor
public class DepartmentController {
    private final DepartmentService departmentService;

    @Operation(summary = "List departments", description = "Returns internal departments as a paginated response. Supports optional text search (`query`) on name/description and optional status filter (`status`). Defaults to active departments when status is omitted. Requires `department.read` permission.")
    @GetMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_READ + "')")
    public ResponseEntity<ApiResponse<PageResponse<DepartmentResponse>>> listDepartments(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean status,
            Pageable pageable
    ) {
        Page<DepartmentResponse> page = departmentService.listDepartments(query, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Departments retrieved successfully", PageResponse.from(page)));
    }

    @Operation(summary = "Get a department", description = "Returns an internal department by ID. Requires `department.read` permission.")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_READ + "')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> getDepartment(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Department retrieved successfully", departmentService.getDepartment(id)));
    }

    @Operation(summary = "Create a department", description = "Creates an internal department. Requires `department.create` permission.")
    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_CREATE + "')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Department created successfully", departmentService.createDepartment(request)));
    }

    @Operation(summary = "Update a department", description = "Updates an internal department. Requires `department.update` permission.")
    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_UPDATE + "')")
    public ResponseEntity<ApiResponse<DepartmentResponse>> updateDepartment(
            @PathVariable String id,
            @Valid @RequestBody DepartmentRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department updated successfully", departmentService.updateDepartment(id, request)));
    }

    @Operation(summary = "Deactivate a department", description = "Soft-deactivates an internal department if no categories are linked to it. Requires `department.delete` permission.")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_DELETE + "')")
    public ResponseEntity<Void> deleteDepartment(@PathVariable String id) {
        departmentService.deleteDepartment(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "List department categories", description = "Returns active incident categories linked to a department. Requires `department.read` permission.")
    @GetMapping("/{id}/categories")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_READ + "')")
    public ResponseEntity<ApiResponse<List<IncidentCategoryResponse>>> listCategories(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Department categories retrieved successfully", departmentService.listCategories(id)));
    }

    @Operation(summary = "Link a category to a department", description = "Assigns an incident category to a department. Requires `department.update` permission.")
    @PostMapping("/{id}/categories/{categoryId}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> addCategory(
            @PathVariable String id,
            @PathVariable String categoryId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department category added successfully", departmentService.addCategory(id, categoryId)));
    }

    @Operation(summary = "Unlink a category from a department", description = "Removes an incident category from a department. Requires `department.update` permission.")
    @DeleteMapping("/{id}/categories/{categoryId}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> removeCategory(
            @PathVariable String id,
            @PathVariable String categoryId
    ) {
        return ResponseEntity.ok(ApiResponse.success("Department category removed successfully", departmentService.removeCategory(id, categoryId)));
    }
}
