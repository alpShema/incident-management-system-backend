package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.services.SeverityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Severities", description = "Incident severity / priority lookup values")
@RestController
@RequestMapping("/severities")
@RequiredArgsConstructor
public class SeverityController {
    private final SeverityService severityService;

    @Operation(summary = "List all severities", description = "Returns all available severity levels (e.g. Low, Medium, High, Critical).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Severities retrieved")
    })
    @GetMapping
    public ResponseEntity<ApiResponse<List<LookupResponse>>> listSeverities() {
        return ResponseEntity.ok(ApiResponse.success("Severities retrieved successfully", severityService.listSeverities()));
    }
}
