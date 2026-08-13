package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TimezoneServiceTest {

    private final TimezoneService timezoneService = new TimezoneService();

    @Test
    void listTimezones_noQuery_returnsFixedDefaultSet() {
        var ids = timezoneService.listTimezones(null).stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).containsExactly("Africa/Accra", "Africa/Kigali", "Europe/Berlin", "UTC");
    }

    @Test
    void listTimezones_blankQuery_behavesLikeNoQuery() {
        var ids = timezoneService.listTimezones("  ").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).containsExactly("Africa/Accra", "Africa/Kigali", "Europe/Berlin", "UTC");
    }

    @Test
    void listTimezones_defaultSet_reportsCorrectOffsets() {
        List<TimezoneOptionResponse> defaults = timezoneService.listTimezones(null);

        assertThat(defaults).contains(
                new TimezoneOptionResponse("Africa/Accra", "Accra", "+00:00"),
                new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00"));
    }

    @Test
    void listTimezones_withQuery_everyResultRoundTripsThroughZoneId() {
        for (TimezoneOptionResponse option : timezoneService.listTimezones("kigali")) {
            assertThat(ZoneId.of(option.id())).isNotNull();
        }
    }

    @Test
    void listTimezones_withQuery_matchesById() {
        var ids = timezoneService.listTimezones("kigali").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).containsExactly("Africa/Kigali");
    }

    @Test
    void listTimezones_withQuery_matchesByLabelEvenAcrossUnderscoreVsSpace() {
        // "America/Los_Angeles" -- searching the space-separated label form must still match
        // even though the raw id uses an underscore.
        var ids = timezoneService.listTimezones("los angeles").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).containsExactly("America/Los_Angeles");
    }

    @Test
    void listTimezones_withQuery_dropsLegacyAliasGroups() {
        // "US/Eastern" is a backward-compat alias of "America/New_York" -- excluded, so this
        // search term (which "US/Eastern" would otherwise match) returns nothing.
        var ids = timezoneService.listTimezones("eastern").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).isEmpty();
    }

    @Test
    void listTimezones_withQuery_dropsEtcGmtOffsetZones() {
        // No real (non-Etc/*) zone matches "gmt" either, so the whole legacy group is gone.
        var ids = timezoneService.listTimezones("gmt").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).isEmpty();
    }

    @Test
    void listTimezones_withQuery_fixedOffsetZone_reportsExpectedOffset() {
        TimezoneOptionResponse kigali = timezoneService.listTimezones("kigali").stream()
                .filter(option -> option.id().equals("Africa/Kigali"))
                .findFirst()
                .orElseThrow();

        assertThat(kigali).isEqualTo(new TimezoneOptionResponse("Africa/Kigali", "Kigali", "+02:00"));
    }

    @Test
    void listTimezones_withQuery_stillMatchesUtc() {
        var ids = timezoneService.listTimezones("utc").stream().map(TimezoneOptionResponse::id).toList();

        assertThat(ids).contains("UTC");
    }

    @Test
    void listTimezones_withBroadQuery_capsResultsAtFive() {
        // A single common letter matches a large fraction of the ~490 real zones -- the response
        // must stay capped regardless, narrowing further is on the admin as they keep typing.
        var results = timezoneService.listTimezones("g");

        assertThat(results).hasSize(5);
    }
}
