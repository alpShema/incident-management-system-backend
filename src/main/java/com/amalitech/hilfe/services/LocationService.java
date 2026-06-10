package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.CreateLocationRequest;
import com.amalitech.hilfe.dto.LocationResponse;
import com.amalitech.hilfe.dto.PageResponse;
import com.amalitech.hilfe.dto.UpdateLocationRequest;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.repositories.LocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locationRepository;

    public PageResponse<LocationResponse> listLocations(String name, Boolean status, Pageable pageable) {
        return PageResponse.from(
                locationRepository.findFiltered(name != null ? "%" + name.toLowerCase() + "%" : null, status, pageable)
                        .map(LocationResponse::from)
        );
    }

    public LocationResponse getLocation(String id) {
        return LocationResponse.from(findById(id));
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        locationRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ArmsAuthException("A location with this name already exists", 409);
        });
        Location location = Location.builder()
                .id("loc-" + UUID.randomUUID().toString().substring(0, 8))
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .status(true)
                .build();
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse updateLocation(String id, UpdateLocationRequest request) {
        Location location = findById(id);
        if (request.name() != null && !request.name().isBlank()) {
            locationRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ArmsAuthException("A location with this name already exists", 409);
                }
            });
            location.setName(request.name().trim());
        }
        if (request.description() != null) {
            location.setDescription(request.description().trim());
        }
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse deactivateLocation(String id) {
        Location location = findById(id);
        if (!Boolean.TRUE.equals(location.getStatus())) {
            throw new ArmsAuthException("Location is already inactive", 409);
        }
        location.setStatus(false);
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse reactivateLocation(String id) {
        Location location = findById(id);
        if (Boolean.TRUE.equals(location.getStatus())) {
            throw new ArmsAuthException("Location is already active", 409);
        }
        location.setStatus(true);
        return LocationResponse.from(locationRepository.save(location));
    }

    private Location findById(String id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Location not found", 404));
    }
}
