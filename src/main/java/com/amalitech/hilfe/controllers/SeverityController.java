package com.amalitech.hilfe.controllers;

import com.amalitech.hilfe.dto.ApiResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.repositories.SeverityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/severities")
@RequiredArgsConstructor
public class SeverityController {
    private final SeverityRepository severityRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<LookupResponse>>> listSeverities() {
        List<LookupResponse> severities = severityRepository.findAll().stream()
                .map(s -> LookupResponse.from(s.getId(), s.getName()))
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Severities retrieved successfully", severities));
    }
}
