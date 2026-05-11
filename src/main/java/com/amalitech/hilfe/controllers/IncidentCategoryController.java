package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentCategoryService;
import com.amalitech.hilfe.services.JwtTokenService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/incident-categories")
@RequiredArgsConstructor
public class IncidentCategoryController {
    private final IncidentCategoryService categoryService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<IncidentCategoryResponse>>> listCategories() {
        return ResponseEntity.ok(ApiResponse.success("Incident categories retrieved successfully", categoryService.listCategories()));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> createCategory(
            @Valid @RequestBody IncidentCategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident category created successfully", categoryService.createCategory(request)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_UPDATE + "')")
    public ResponseEntity<ApiResponse<IncidentCategoryResponse>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody IncidentCategoryRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Incident category updated successfully", categoryService.updateCategory(id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_CATEGORY_DELETE + "')")
    public ResponseEntity<Void> deleteCategory(@PathVariable String id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/topics")
    public ResponseEntity<ApiResponse<List<IncidentTopicResponse>>> listTopics(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.success("Incident topics retrieved successfully", categoryService.listTopicsByCategory(id)));
    }

    @PostMapping("/{id}/topics")
    @PreAuthorize("hasAuthority('" + RbacPermissions.INCIDENT_TYPE_CREATE + "')")
    public ResponseEntity<ApiResponse<IncidentTopicResponse>> createTopic(
            @PathVariable String id,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal,
            @Valid @RequestBody CreateTopicRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Incident topic created successfully", categoryService.createTopic(id, principal.userId(), request)));
    }
}
