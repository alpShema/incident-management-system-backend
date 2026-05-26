package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.services.IncidentCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Incident Topics", description = "Global incident topic listing across categories and agent groups")
@RestController
@RequestMapping("/incident-topics")
@RequiredArgsConstructor
public class IncidentTopicController {

    private final IncidentCategoryService incidentCategoryService;

    @Operation(
            summary = "List incident topics",
            description = "Returns a paginated list of incident topics globally. "
                    + "Supports filtering by category, department, agent group, status (active/inactive by category status), "
                    + "and text query across topic/category/agent-group names."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topics retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid status filter")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<IncidentTopicListResponse>>> listTopics(
            @Parameter(description = "Filter by category ID") @RequestParam(required = false) String categoryId,
            @Parameter(description = "Filter by department ID (from topic's category)") @RequestParam(required = false) String departmentId,
            @Parameter(description = "Filter by assigned agent group ID") @RequestParam(required = false) String agentGroupId,
            @Parameter(description = "Filter by category status: active (default) or inactive")
            @RequestParam(required = false, defaultValue = "active") String status,
            @Parameter(description = "Search text over topic name/description, category name, and agent-group name")
            @RequestParam(required = false) String query,
            Pageable pageable
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Incident topics retrieved successfully",
                PageResponse.from(incidentCategoryService.listTopics(
                        categoryId, departmentId, agentGroupId, status, query, pageable))));
    }
}
