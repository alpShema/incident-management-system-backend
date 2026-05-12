package com.amalitech.hilfe.services;

import com.amalitech.hilfe.config.CacheConfig;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.repositories.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LocationService {
    private final LocationRepository locationRepository;

    @Cacheable(CacheConfig.LOCATIONS)
    public List<LocationResponse> listLocations() {
        return locationRepository.findAll().stream()
                .map(LocationResponse::from)
                .toList();
    }
}
