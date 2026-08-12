package com.amalitech.hilfe.utils;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Business-hours-aware equivalents of {@code Instant.plus}/{@code Duration.between}: nights and
 * weekends outside {@link BusinessHours} don't count toward elapsed or remaining time. All
 * arithmetic happens in zone-local {@link LocalDateTime} space, converting to/from {@link Instant}
 * only at the boundary, so the day-by-day walk stays correct across DST regardless of which zone
 * an admin picks.
 */
public final class BusinessHoursCalculator {

    private BusinessHoursCalculator() {
    }

    public record BusinessHours(ZoneId zone, LocalTime start, LocalTime end) {
    }

    /**
     * Rolls {@code start} forward into the next open business window if it falls outside one
     * (night/weekend), then walks day-by-day consuming {@code minutes}, skipping Sat/Sun.
     */
    public static Instant addBusinessMinutes(Instant start, long minutes, BusinessHours hours) {
        if (start == null) {
            return null;
        }
        ZoneId zone = hours.zone();
        LocalDateTime cursor = rollForwardToBusinessOpen(LocalDateTime.ofInstant(start, zone), hours);
        long remaining = minutes;
        while (remaining > 0) {
            long availableToday = Duration.between(cursor.toLocalTime(), hours.end()).toMinutes();
            if (remaining <= availableToday) {
                cursor = cursor.plusMinutes(remaining);
                remaining = 0;
            } else {
                remaining -= availableToday;
                cursor = LocalDateTime.of(nextBusinessDay(cursor.toLocalDate()), hours.start());
            }
        }
        return cursor.atZone(zone).toInstant();
    }

    /**
     * Sums business-minutes across the day-by-day range between {@code from} and {@code to},
     * clipping partial first/last days to the business window and skipping weekends entirely.
     */
    public static long businessMinutesBetween(Instant from, Instant to, BusinessHours hours) {
        if (from == null || to == null || !from.isBefore(to)) {
            return 0L;
        }
        ZoneId zone = hours.zone();
        LocalDateTime start = LocalDateTime.ofInstant(from, zone);
        LocalDateTime end = LocalDateTime.ofInstant(to, zone);
        LocalDate date = start.toLocalDate();
        LocalDate endDate = end.toLocalDate();

        long total = 0L;
        while (!date.isAfter(endDate)) {
            if (!isWeekend(date)) {
                LocalTime dayStart = date.isEqual(start.toLocalDate()) ? max(hours.start(), start.toLocalTime()) : hours.start();
                LocalTime dayEnd = date.isEqual(endDate) ? min(hours.end(), end.toLocalTime()) : hours.end();
                if (dayStart.isBefore(dayEnd)) {
                    total += Duration.between(dayStart, dayEnd).toMinutes();
                }
            }
            date = date.plusDays(1);
        }
        return total;
    }

    private static LocalDateTime rollForwardToBusinessOpen(LocalDateTime dt, BusinessHours hours) {
        LocalDate date = dt.toLocalDate();
        if (isWeekend(date)) {
            return LocalDateTime.of(rollToWeekday(date), hours.start());
        }
        LocalTime time = dt.toLocalTime();
        if (time.isBefore(hours.start())) {
            return LocalDateTime.of(date, hours.start());
        }
        if (!time.isBefore(hours.end())) {
            return LocalDateTime.of(nextBusinessDay(date), hours.start());
        }
        return dt;
    }

    private static LocalDate nextBusinessDay(LocalDate date) {
        return rollToWeekday(date.plusDays(1));
    }

    private static LocalDate rollToWeekday(LocalDate date) {
        LocalDate cursor = date;
        while (isWeekend(cursor)) {
            cursor = cursor.plusDays(1);
        }
        return cursor;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow.equals(DayOfWeek.SATURDAY) || dow.equals(DayOfWeek.SUNDAY);
    }

    private static LocalTime max(LocalTime a, LocalTime b) {
        return a.isAfter(b) ? a : b;
    }

    private static LocalTime min(LocalTime a, LocalTime b) {
        return a.isBefore(b) ? a : b;
    }
}
