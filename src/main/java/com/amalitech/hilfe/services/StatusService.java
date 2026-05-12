package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.CacheConfig;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.repositories.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusService {
    private final StatusRepository statusRepository;

    @Cacheable(CacheConfig.STATUSES)
    public List<LookupResponse> listStatuses() {
        return statusRepository.findAll().stream()
                .map(s -> LookupResponse.from(s.getId(), s.getName()))
                .toList();
    }
}
