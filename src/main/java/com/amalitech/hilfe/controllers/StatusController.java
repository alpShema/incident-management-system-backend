package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.services.StatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Statuses", description = "Incident status lookup values")
@RestController
@RequestMapping("/statuses")
@RequiredArgsConstructor
public class StatusController {
    private final StatusService statusService;

    @Operation(summary = "List all statuses", description = "Returns all available incident statuses in lifecycle order: Open → In Progress → Pending → Closed")
    @GetMapping
    public ResponseEntity<ApiResponse<List<LookupResponse>>> listStatuses() {
        return ResponseEntity.ok(ApiResponse.success("Statuses retrieved successfully", statusService.listStatuses()));
    }
}
