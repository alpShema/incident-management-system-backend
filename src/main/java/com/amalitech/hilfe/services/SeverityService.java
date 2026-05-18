package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.repositories.SeverityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SeverityService {
    private final SeverityRepository severityRepository;

    public List<LookupResponse> listSeverities() {
        return severityRepository.findAll().stream()
                .map(s -> LookupResponse.from(s.getId(), s.getName()))
                .toList();
    }
}
