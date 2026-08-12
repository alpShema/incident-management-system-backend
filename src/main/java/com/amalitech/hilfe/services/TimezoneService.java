package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.TimezoneOptionResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Backs the location create/edit form's timezone dropdown with the JVM's own bundled IANA
 * database -- the same source {@code ZoneId.of(...)} validation on Location already checks
 * against, so nothing this returns can ever fail that validation.
 *
 * <p>The full IANA set is ~490 real zones after dropping legacy aliases, too many to dump into a
 * dropdown at once. {@link #listTimezones(String)} defaults to a short, fixed list of
 * AmaliTech's known locations, and only searches the full set once the admin actually starts
 * typing (e.g. for a brand-new office in a region not in the default list yet) -- search results
 * are capped at {@link #MAX_SEARCH_RESULTS}, narrowing further as the admin keeps typing.
 */
@Service
public class TimezoneService {

    private static final String UTC = "UTC";

    // Caps a search response to a manageable dropdown size -- a short/common term (e.g. a single
    // letter) can otherwise match a large fraction of the ~490 real zones. Callers narrow the
    // term as the admin keeps typing rather than paging through more than this at once.
    private static final int MAX_SEARCH_RESULTS = 5;

    // Fixed, not derived from what's already in the Location table -- deriving it from existing
    // locations is circular for the one case that matters most: picking a timezone for the very
    // first location in a new region, before anything with that zone exists yet.
    private static final List<String> DEFAULT_TIMEZONE_IDS = List.of(
            "Africa/Accra",   // Ghana office
            "Africa/Kigali",  // Rwanda office
            "Europe/Berlin",  // Cologne
            UTC               // system default
    );

    // Backward-compat alias groups: each of these duplicates a real Region/City zone already in
    // the list under a different name (e.g. "US/Eastern" == "America/New_York"), or -- for Etc/*
    // and SystemV/* -- isn't a real place at all. Dropping them loses no real location.
    private static final Set<String> LEGACY_ALIAS_PREFIXES =
            Set.of("Etc", "US", "Canada", "Mexico", "Brazil", "Chile", "SystemV");

    public List<TimezoneOptionResponse> listTimezones(String query) {
        return (query == null || query.isBlank()) ? defaultOptions() : search(query);
    }

    private List<TimezoneOptionResponse> defaultOptions() {
        Instant now = Instant.now();
        return DEFAULT_TIMEZONE_IDS.stream()
                .map(id -> toOption(id, now))
                .toList();
    }

    private List<TimezoneOptionResponse> search(String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        return ZoneId.getAvailableZoneIds().stream()
                .filter(this::isSelectable)
                .filter(id -> matches(id, needle))
                .sorted()
                .limit(MAX_SEARCH_RESULTS)
                .map(id -> toOption(id, now))
                .toList();
    }

    private boolean matches(String id, String needle) {
        return id.toLowerCase(Locale.ROOT).contains(needle) || toLabel(id).toLowerCase(Locale.ROOT).contains(needle);
    }

    // Drops obscure legacy 3-letter aliases (e.g. "EST", "PST") and backward-compat alias groups
    // -- keeps region-qualified "Continent/City" ids plus UTC, the system default.
    private boolean isSelectable(String id) {
        if (id.equals(UTC)) {
            return true;
        }
        if (!id.contains("/")) {
            return false;
        }
        String prefix = id.substring(0, id.indexOf('/'));
        return !LEGACY_ALIAS_PREFIXES.contains(prefix);
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
