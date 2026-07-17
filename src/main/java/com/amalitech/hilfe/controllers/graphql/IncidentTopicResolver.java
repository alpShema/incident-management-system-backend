package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateTopicRequest;
import com.amalitech.hilfe.services.IncidentCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class IncidentTopicResolver {

    private final IncidentCategoryService categoryService;

    @QueryMapping
    public PageResponse<IncidentTopicListResponse> incidentTopics(
            @Argument String categoryId,
            @Argument String departmentId,
            @Argument String agentGroupId,
            @Argument Boolean status,
            @Argument String query,
            @Argument PageInput page) {
        return PageInput.toPageResponse(
                categoryService.listTopics(categoryId, departmentId, agentGroupId, status, query, PageInput.toPageable(page))
        );
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-type.update')")
    public IncidentTopicResponse updateTopicById(@Argument String id, @Argument UpdateTopicRequest input) {
        GraphQlResponseMessage.set("Incident topic updated successfully");
        return categoryService.updateTopicById(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('incident-type.delete')")
    public IncidentTopicResponse updateTopicStatus(@Argument String id, @Argument UpdateTopicStatusInput input) {
        GraphQlResponseMessage.set("Incident topic status updated successfully");
        return categoryService.updateTopicStatus(id, input.status());
    }

    public record UpdateTopicStatusInput(Boolean status) {}
}
