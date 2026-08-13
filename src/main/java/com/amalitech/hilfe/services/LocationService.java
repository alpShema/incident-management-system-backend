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

import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LocationService {

    private static final String LOCATION_ALREADY_EXISTS_MESSAGE = "A location with this name already exists. Please choose a different name.";
    private static final String INVALID_BUSINESS_HOURS_MESSAGE = "Business hours start must be before business hours end.";

    private final LocationRepository locationRepository;
    private final ActivityLogService activityLogService;

    public PageResponse<LocationResponse> listLocations(String query, Boolean status, Pageable pageable) {
        return PageResponse.from(
                locationRepository.findFiltered(query != null ? "%" + query.toLowerCase() + "%" : null, status, pageable)
                        .map(LocationResponse::from)
        );
    }

    public LocationResponse getLocation(String id) {
        return LocationResponse.from(findById(id));
    }

    @Transactional
    public LocationResponse createLocation(CreateLocationRequest request) {
        locationRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
            throw new ArmsAuthException(LOCATION_ALREADY_EXISTS_MESSAGE, 409);
        });
        Location location = Location.builder()
                .id("loc-" + UUID.randomUUID().toString().substring(0, 8))
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : null)
                .status(true)
                .build();
        if (request.timezone() != null) {
            location.setTimezone(request.timezone());
        }
        if (request.businessHoursStart() != null) {
            location.setBusinessHoursStart(request.businessHoursStart());
        }
        if (request.businessHoursEnd() != null) {
            location.setBusinessHoursEnd(request.businessHoursEnd());
        }
        validateBusinessHours(location.getBusinessHoursStart(), location.getBusinessHoursEnd());
        return LocationResponse.from(locationRepository.save(location));
    }

    @Transactional
    public LocationResponse updateLocation(String actorUserId, String id, UpdateLocationRequest request) {
        Location location = findById(id);
        String previousTimezone = location.getTimezone();
        LocalTime previousStart = location.getBusinessHoursStart();
        LocalTime previousEnd = location.getBusinessHoursEnd();

        if (request.name() != null && !request.name().isBlank()) {
            locationRepository.findByNameIgnoreCase(request.name()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new ArmsAuthException(LOCATION_ALREADY_EXISTS_MESSAGE, 409);
                }
            });
            location.setName(request.name().trim());
        }
        if (request.description() != null) {
            location.setDescription(request.description().trim());
        }
        if (request.timezone() != null) {
            location.setTimezone(request.timezone());
        }
        if (request.businessHoursStart() != null) {
            location.setBusinessHoursStart(request.businessHoursStart());
        }
        if (request.businessHoursEnd() != null) {
            location.setBusinessHoursEnd(request.businessHoursEnd());
        }
        validateBusinessHours(location.getBusinessHoursStart(), location.getBusinessHoursEnd());
        LocationResponse response = LocationResponse.from(locationRepository.save(location));
        logLocationChanges(actorUserId, id, previousTimezone, previousStart, previousEnd, location);
        return response;
    }

    private void logLocationChanges(String actorUserId, String locationId,
            String previousTimezone, LocalTime previousStart, LocalTime previousEnd, Location updated) {
        if (!Objects.equals(previousTimezone, updated.getTimezone())) {
            activityLogService.logLocationTimezoneChanged(actorUserId, locationId, previousTimezone, updated.getTimezone());
        }
        if (!Objects.equals(previousStart, updated.getBusinessHoursStart()) || !Objects.equals(previousEnd, updated.getBusinessHoursEnd())) {
            activityLogService.logLocationBusinessHoursChanged(
                    actorUserId, locationId, previousStart, previousEnd,
                    updated.getBusinessHoursStart(), updated.getBusinessHoursEnd());
        }
    }

    private void validateBusinessHours(LocalTime start, LocalTime end) {
        if (start != null && end != null && !start.isBefore(end)) {
            throw new ArmsAuthException(INVALID_BUSINESS_HOURS_MESSAGE, 409);
        }
    }

    @Transactional
    public LocationResponse updateStatus(String id, boolean newStatus) {
        Location location = findById(id);
        if (Boolean.valueOf(newStatus).equals(location.getStatus())) {
            throw new ArmsAuthException("Location is already " + (newStatus ? "active" : "inactive"), 409);
        }
        location.setStatus(newStatus);
        return LocationResponse.from(locationRepository.save(location));
    }

    private Location findById(String id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Location not found", 404));
    }
}
