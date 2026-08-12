package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneServiceTest {

    private final TimezoneService timezoneService = new TimezoneService();

    @Test
    void listTimezones_returnsNonEmptyList() {
        assertThat(timezoneService.listTimezones()).isNotEmpty();
    }

    @Test
    void listTimezones_everyIdRoundTripsThroughZoneId() {
        for (TimezoneOptionResponse option : timezoneService.listTimezones()) {
            assertThat(ZoneId.of(option.id())).isNotNull();
        }
    }

    @Test
    void listTimezones_fixedOffsetZone_reportsExpectedOffset() {
        TimezoneOptionResponse kigali = timezoneService.listTimezones().stream()
                .filter(option -> option.id().equals("Africa/Kigali"))
                .findFirst()
                .orElseThrow();

        assertThat(kigali).isEqualTo(new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00"));
    }

    @Test
    void listTimezones_dropsLegacyThreeLetterAliasesButKeepsUtc() {
        var ids = timezoneService.listTimezones().stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).doesNotContain("EST", "PST", "MST", "HST").contains("UTC");
    }
}
