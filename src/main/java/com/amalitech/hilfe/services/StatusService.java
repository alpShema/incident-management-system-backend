package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.repositories.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusService {
    private final StatusRepository statusRepository;

    public List<StatusLookupResponse> listStatuses() {
        return statusRepository.findAll().stream()
                .map(s -> StatusLookupResponse.from(s.getId(), s.getName(), s.getDescription()))
                .toList();
    }
}
