package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

/**
 * HV-1674: resolves the {@link BusinessHours} window a location's incidents should be timed
 * against. Extracted so every work-hours-aware timer -- SLA response/resolution (SlaService) and
 * the resolved-incident reopen window (IncidentService/AutoCloseService) -- agrees on the same
 * fallback rules instead of drifting apart.
 */
@Component
@RequiredArgsConstructor
public class BusinessHoursResolver {

    static final BusinessHours DEFAULT_BUSINESS_HOURS =
            new BusinessHours(ZoneId.of("UTC"), LocalTime.of(8, 0), LocalTime.of(17, 30));

    private final LocationRepository locationRepository;

    /**
     * Falls back to UTC 08:00-17:30 when the location, its timezone, or its hours are missing or
     * invalid -- this is what keeps every business-hours calculation defined even for incidents
     * whose location record predates this feature or was deleted after the fact.
     */
    public BusinessHours resolve(Location location) {
        if (location == null || location.getTimezone() == null
                || location.getBusinessHoursStart() == null || location.getBusinessHoursEnd() == null) {
            return DEFAULT_BUSINESS_HOURS;
        }
        try {
            return new BusinessHours(ZoneId.of(location.getTimezone()),
                    location.getBusinessHoursStart(), location.getBusinessHoursEnd());
        } catch (Exception e) {
            return DEFAULT_BUSINESS_HOURS;
        }
    }

    public BusinessHours resolveByLocationId(String locationId) {
        if (locationId == null) {
            return DEFAULT_BUSINESS_HOURS;
        }
        return locationRepository.findById(locationId)
                .map(this::resolve)
                .orElse(DEFAULT_BUSINESS_HOURS);
    }
}
