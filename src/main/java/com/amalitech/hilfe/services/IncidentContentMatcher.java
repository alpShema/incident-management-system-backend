package com.amalitech.hilfe.services;

import com.amalitech.hilfe.models.Incident;
import org.springframework.data.domain.Sort;

import java.util.Comparator;

/**
 * Shared by IncidentService and DashboardService: both list incidents whose title/description are
 * encrypted at rest (see com.amalitech.hilfe.crypto) and so can no longer be matched or ordered via
 * SQL -- both decrypt a structurally-scoped candidate set and filter/sort it here instead.
 */
final class IncidentContentMatcher {

    private IncidentContentMatcher() {
    }

    static boolean matches(Incident incident, String lowerQuery) {
        return containsIgnoreCase(incident.getTitle(), lowerQuery)
                || containsIgnoreCase(incident.getDescription(), lowerQuery)
                || containsIgnoreCase(topicName(incident), lowerQuery)
                || containsIgnoreCase(categoryName(incident), lowerQuery)
                || String.valueOf(incident.getIncidentNo()).contains(lowerQuery);
    }

    // Mirrors Postgres's default ORDER BY for the same fields this replaces -- case-insensitive
    // for text, though it won't reproduce Postgres's exact Unicode collation for non-ASCII titles.
    static Comparator<Incident> buildComparator(Sort sort) {
        Comparator<Incident> comparator = null;
        for (Sort.Order order : sort) {
            Comparator<Incident> fieldComparator = comparatorForField(order.getProperty());
            if (order.isDescending()) {
                fieldComparator = fieldComparator.reversed();
            }
            comparator = comparator == null ? fieldComparator : comparator.thenComparing(fieldComparator);
        }
        return comparator != null ? comparator : comparatorForField("createdAt").reversed();
    }

    private static Comparator<Incident> comparatorForField(String property) {
        return switch (property) {
            case "title" -> Comparator.comparing(Incident::getTitle, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "updatedAt" -> Comparator.comparing(Incident::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "incidentNo" -> Comparator.comparing(Incident::getIncidentNo, Comparator.nullsLast(Comparator.naturalOrder()));
            case "incidentType.category.name" -> Comparator.comparing(IncidentContentMatcher::categoryName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "severity.name" -> Comparator.comparing(
                    i -> i.getSeverity() != null ? i.getSeverity().getName() : null,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "status.name" -> Comparator.comparing(
                    i -> i.getStatus() != null ? i.getStatus().getName() : null,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            default -> Comparator.comparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private static boolean containsIgnoreCase(String value, String lowerQuery) {
        return value != null && value.toLowerCase().contains(lowerQuery);
    }

    private static String topicName(Incident incident) {
        return incident.getIncidentType() != null ? incident.getIncidentType().getName() : null;
    }

    private static String categoryName(Incident incident) {
        return incident.getIncidentType() != null && incident.getIncidentType().getCategory() != null
                ? incident.getIncidentType().getCategory().getName() : null;
    }
}
