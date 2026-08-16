package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateIncidentCategoryRequest;
import com.amalitech.hilfe.dto.UpdateTopicRequest;
import com.amalitech.hilfe.security.authorization.RbacPermissions;
import com.amalitech.hilfe.services.IncidentCategoryService;
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
public class IncidentCategoryResolver {

    private final IncidentCategoryService categoryService;

    @QueryMapping
    public PageResponse<IncidentCategoryResponse> incidentCategories(
            @Argument Boolean status,
            @Argument Boolean hasActiveTopics,
            @Argument String query,
            @Argument PageInput page    ) {
        return PageInput.toPageResponse(
                categoryService.listCategories(status, hasActiveTopics, query, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'ADMIN_AGENT', 'SUPER_ADMIN')")
    public PageResponse<IncidentCategoryResponse> allIncidentCategories(
            @Argument Boolean status,
            @Argument Boolean hasActiveTopics,
            @Argument String query,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                categoryService.listAllCategories(status, hasActiveTopics, query, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('" + RbacPermissions.DEPARTMENT_READ + "')")
    public PageResponse<IncidentCategoryResponse> incidentCategoriesByDepartment(
            @Argument String departmentId,
            @Argument Boolean status,
            @Argument String query,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                categoryService.listCategoriesByDepartment(departmentId, status, query, PageInput.toPageable(page))
        );
    }

    @QueryMapping
    public List<IncidentTopicResponse> categoryTopics(@Argument String categoryId, @Argument Boolean status) {
        return categoryService.listTopicsByCategory(categoryId, status);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-category.create')")
    public IncidentCategoryResponse createIncidentCategory(@Valid @Argument IncidentCategoryRequest input) {
        GraphQlResponseMessage.set("Incident category created successfully");
        return categoryService.createCategory(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-category.update')")
    public IncidentCategoryResponse updateIncidentCategory(@Argument String id, @Valid @Argument UpdateIncidentCategoryRequest input) {
        GraphQlResponseMessage.set("Incident category updated successfully");
        return categoryService.updateCategory(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-category.delete')")
    public IncidentCategoryResponse updateIncidentCategoryStatus(@Argument String id, @Valid @Argument UpdateIncidentCategoryStatusInput input) {
        GraphQlResponseMessage.set("Incident category status updated successfully");
        return categoryService.updateCategoryStatus(id, input.status());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-type.create')")
    public IncidentTopicResponse createCategoryTopic(
            @Argument String categoryId,
            @Valid @Argument CreateTopicRequest input,
            @AuthenticationPrincipal JwtTokenService.AuthPrincipal principal) {
        GraphQlResponseMessage.set("Incident topic created successfully");
        return categoryService.createTopic(categoryId, principal.userId(), input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-type.update')")
    public IncidentTopicResponse updateCategoryTopic(
            @Argument String categoryId,
            @Argument String topicId,
            @Valid @Argument UpdateTopicRequest input) {
        GraphQlResponseMessage.set("Incident topic updated successfully");
        return categoryService.updateTopic(categoryId, topicId, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-type.delete')")
    public boolean deleteCategoryTopic(@Argument String categoryId, @Argument String topicId) {
        categoryService.deleteTopic(categoryId, topicId);
        GraphQlResponseMessage.set("Incident topic deleted successfully");
        return true;
    }

    public record UpdateIncidentCategoryStatusInput(Boolean status) {}
}
