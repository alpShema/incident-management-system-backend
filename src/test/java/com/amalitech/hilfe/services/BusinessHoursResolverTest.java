package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Location;
import com.amalitech.hilfe.repositories.LocationRepository;
import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessHoursResolverTest {

    private static final BusinessHours DEFAULT_HOURS =
            new BusinessHours(ZoneId.of("UTC"), LocalTime.of(8, 0), LocalTime.of(17, 30));

    @Mock LocationRepository locationRepository;
    @InjectMocks BusinessHoursResolver businessHoursResolver;

    @Test
    void resolve_validLocation_usesItsOwnTimezoneAndHours() {
        Location location = Location.builder()
                .timezone("Africa/Accra")
                .businessHoursStart(LocalTime.of(9, 0))
                .businessHoursEnd(LocalTime.of(18, 0))
                .build();

        BusinessHours hours = businessHoursResolver.resolve(location);

        assertThat(hours.zone()).isEqualTo(ZoneId.of("Africa/Accra"));
        assertThat(hours.start()).isEqualTo(LocalTime.of(9, 0));
        assertThat(hours.end()).isEqualTo(LocalTime.of(18, 0));
    }

    @Test
    void resolve_nullLocation_fallsBackToUtcDefault() {
        assertThat(businessHoursResolver.resolve(null)).isEqualTo(DEFAULT_HOURS);
    }

    @Test
    void resolve_missingTimezoneOrHours_fallsBackToUtcDefault() {
        // Location has @Builder.Default values for these fields, so they must be explicitly
        // nulled out here to exercise the "predates this feature" fallback branch at all.
        Location location = Location.builder()
                .timezone(null).businessHoursStart(null).businessHoursEnd(null).build();

        assertThat(businessHoursResolver.resolve(location)).isEqualTo(DEFAULT_HOURS);
    }

    @Test
    void resolve_invalidTimezone_fallsBackToUtcDefault() {
        Location location = Location.builder()
                .timezone("Not/A_Real_Zone")
                .businessHoursStart(LocalTime.of(9, 0))
                .businessHoursEnd(LocalTime.of(18, 0))
                .build();

        assertThat(businessHoursResolver.resolve(location)).isEqualTo(DEFAULT_HOURS);
    }

    @Test
    void resolveByLocationId_nullId_fallsBackToUtcDefault() {
        assertThat(businessHoursResolver.resolveByLocationId(null)).isEqualTo(DEFAULT_HOURS);
    }

    @Test
    void resolveByLocationId_locationNotFound_fallsBackToUtcDefault() {
        when(locationRepository.findById("missing")).thenReturn(Optional.empty());

        assertThat(businessHoursResolver.resolveByLocationId("missing")).isEqualTo(DEFAULT_HOURS);
    }

    @Test
    void resolveByLocationId_locationFound_usesItsHours() {
        Location location = Location.builder()
                .timezone("Africa/Accra")
                .businessHoursStart(LocalTime.of(9, 0))
                .businessHoursEnd(LocalTime.of(18, 0))
                .build();
        when(locationRepository.findById("loc-1")).thenReturn(Optional.of(location));

        BusinessHours hours = businessHoursResolver.resolveByLocationId("loc-1");

        assertThat(hours.zone()).isEqualTo(ZoneId.of("Africa/Accra"));
    }
}
