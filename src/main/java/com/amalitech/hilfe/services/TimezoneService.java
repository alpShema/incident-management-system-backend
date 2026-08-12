package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Backs the location create/edit form's timezone dropdown with the JVM's own bundled IANA
 * database -- the same source {@code ZoneId.of(...)} validation on Location already checks
 * against, so nothing this returns can ever fail that validation.
 */
@Service
public class TimezoneService {

    public List<TimezoneOptionResponse> listTimezones() {
        Instant now = Instant.now();
        return ZoneId.getAvailableZoneIds().stream()
                .filter(this::isSelectable)
                .sorted()
                .map(id -> toOption(id, now))
                .toList();
    }

    // Drops obscure legacy 3-letter aliases (e.g. "EST", "PST") that duplicate a proper
    // Region/City id -- keeps region-qualified ids plus UTC, the system default.
    private boolean isSelectable(String id) {
        return id.contains("/") || id.equals("UTC");
    }

    private TimezoneOptionResponse toOption(String id, Instant now) {
        ZoneOffset offset = ZoneId.of(id).getRules().getOffset(now);
        return new TimezoneOptionResponse(id, toLabel(id), formatOffset(offset));
    }

    private String toLabel(String id) {
        String lastSegment = id.contains("/") ? id.substring(id.lastIndexOf('/') + 1) : id;
        return lastSegment.replace('_', ' ');
    }

    private String formatOffset(ZoneOffset offset) {
        int totalSeconds = offset.getTotalSeconds();
        char sign = totalSeconds < 0 ? '-' : '+';
        int absSeconds = Math.abs(totalSeconds);
        return String.format("%c%02d:%02d", sign, absSeconds / 3600, (absSeconds % 3600) / 60);
    }
}
