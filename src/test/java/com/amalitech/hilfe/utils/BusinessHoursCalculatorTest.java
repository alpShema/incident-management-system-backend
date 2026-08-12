package com.amalitech.hilfe.utils;

import com.amalitech.hilfe.utils.BusinessHoursCalculator.BusinessHours;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessHoursCalculatorTest {

    // Africa/Kigali: fixed UTC+2, no DST -- isolates the day-by-day logic from any offset changes.
    private static final ZoneId KIGALI = ZoneId.of("Africa/Kigali");
    private static final BusinessHours KIGALI_HOURS = new BusinessHours(KIGALI, LocalTime.of(8, 0), LocalTime.of(17, 30));

    // 2026-08-07 is a Friday, 2026-08-08/09 a Sat/Sun, 2026-08-10 a Monday.
    private static final LocalDate FRI = LocalDate.of(2026, Month.AUGUST, 7);
    private static final LocalDate SAT = LocalDate.of(2026, Month.AUGUST, 8);
    private static final LocalDate SUN = LocalDate.of(2026, Month.AUGUST, 9);
    private static final LocalDate MON = LocalDate.of(2026, Month.AUGUST, 10);

    private static Instant at(ZoneId zone, LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, zone).toInstant();
    }

    @Test
    void addBusinessMinutes_sameDayAddition_staysWithinWindow() {
        Instant start = at(KIGALI, MON, LocalTime.of(9, 0));

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, 60, KIGALI_HOURS);

        assertThat(result).isEqualTo(at(KIGALI, MON, LocalTime.of(10, 0)));
    }

    @Test
    void addBusinessMinutes_createdAfterHours_rollsToNextBusinessDayStart() {
        Instant start = at(KIGALI, FRI, LocalTime.of(20, 0));

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, 30, KIGALI_HOURS);

        assertThat(result).isEqualTo(at(KIGALI, MON, LocalTime.of(8, 30)));
    }

    @Test
    void addBusinessMinutes_createdOnWeekend_rollsToMondayStart() {
        Instant start = at(KIGALI, SAT, LocalTime.of(10, 0));

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, 15, KIGALI_HOURS);

        assertThat(result).isEqualTo(at(KIGALI, MON, LocalTime.of(8, 15)));
    }

    @Test
    void addBusinessMinutes_createdExactlyAtClose_rollsToNextDayAndDoesNotCount() {
        Instant start = at(KIGALI, MON, LocalTime.of(17, 30));

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, 15, KIGALI_HOURS);

        assertThat(result).isEqualTo(at(KIGALI, LocalDate.of(2026, Month.AUGUST, 11), LocalTime.of(8, 15)));
    }

    @Test
    void addBusinessMinutes_thresholdSpansMultipleWeekends() {
        Instant start = at(KIGALI, FRI, LocalTime.of(16, 0));
        // 90 min left on Friday + 5 full business days (Mon-Fri, 570 min each) + 210 min into the
        // following Monday -- crosses two separate weekends (Aug 8-9 and Aug 15-16).
        long minutes = 90 + (5 * 570) + 210;

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, minutes, KIGALI_HOURS);

        assertThat(result).isEqualTo(at(KIGALI, LocalDate.of(2026, Month.AUGUST, 17), LocalTime.of(11, 30)));
    }

    @Test
    void businessMinutesBetween_spansAWeekend_excludesIt() {
        Instant from = at(KIGALI, FRI, LocalTime.of(16, 0));
        Instant to = at(KIGALI, MON, LocalTime.of(9, 0));

        long minutes = BusinessHoursCalculator.businessMinutesBetween(from, to, KIGALI_HOURS);

        assertThat(minutes).isEqualTo(90 + 60); // 90 min left on Friday, 60 min into Monday
    }

    @Test
    void businessMinutesBetween_entirelyWithinAWeekend_isZero() {
        Instant from = at(KIGALI, SAT, LocalTime.of(10, 0));
        Instant to = at(KIGALI, SUN, LocalTime.of(15, 0));

        long minutes = BusinessHoursCalculator.businessMinutesBetween(from, to, KIGALI_HOURS);

        assertThat(minutes).isZero();
    }

    @Test
    void businessMinutesBetween_dstSpringForwardWeekend_matchesWallClockDuration() {
        // Europe/London observes DST; clocks spring forward on 2026-03-29. The result must match
        // the wall-clock-only calculation (150 min) even though the underlying UTC offset shifts
        // from GMT to BST partway through the range.
        ZoneId london = ZoneId.of("Europe/London");
        BusinessHours londonHours = new BusinessHours(london, LocalTime.of(8, 0), LocalTime.of(17, 30));
        LocalDate fri = LocalDate.of(2026, Month.MARCH, 27);
        LocalDate mon = LocalDate.of(2026, Month.MARCH, 30);

        Instant from = at(london, fri, LocalTime.of(16, 0));
        Instant to = at(london, mon, LocalTime.of(9, 0));

        long minutes = BusinessHoursCalculator.businessMinutesBetween(from, to, londonHours);

        assertThat(minutes).isEqualTo(90 + 60);
    }

    @Test
    void addBusinessMinutes_dstSpringForwardWeekend_landsAtCorrectLocalTime() {
        ZoneId london = ZoneId.of("Europe/London");
        BusinessHours londonHours = new BusinessHours(london, LocalTime.of(8, 0), LocalTime.of(17, 30));
        LocalDate fri = LocalDate.of(2026, Month.MARCH, 27);
        LocalDate mon = LocalDate.of(2026, Month.MARCH, 30);

        Instant start = at(london, fri, LocalTime.of(16, 0));

        Instant result = BusinessHoursCalculator.addBusinessMinutes(start, 90 + 570, londonHours);

        assertThat(result).isEqualTo(at(london, mon, LocalTime.of(17, 30)));
    }
}
