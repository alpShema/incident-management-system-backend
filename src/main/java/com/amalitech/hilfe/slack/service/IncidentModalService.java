package com.amalitech.hilfe.slack.service;

import com.amalitech.hilfe.config.SlackProperties;
import com.amalitech.hilfe.dto.CreateIncidentRequest;
import com.amalitech.hilfe.dto.IncidentDateFilter;
import com.amalitech.hilfe.dto.IncidentFilterParams;
import com.amalitech.hilfe.dto.IncidentResponse;
import com.amalitech.hilfe.models.*;
import com.amalitech.hilfe.repositories.*;
import com.amalitech.hilfe.services.IncidentService;
import com.amalitech.hilfe.slack.client.SlackClient;
import com.amalitech.hilfe.slack.exception.SlackNotConnectedException;
import com.amalitech.hilfe.slack.exception.SlackPermissionDeniedException;
import com.fasterxml.jackson.databind.JsonNode;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.view.View;
import com.slack.api.model.view.ViewTitle;
import com.slack.api.model.view.ViewSubmit;
import com.slack.api.model.view.ViewClose;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.slack.api.model.block.Blocks.*;
import static com.slack.api.model.block.composition.BlockCompositions.*;
import static com.slack.api.model.block.element.BlockElements.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "slack.enabled", havingValue = "true")
public class IncidentModalService {

    private static final String TYPE_BLOCK = "type_block";
    private static final String TITLE_BLOCK = "title_block";
    private static final String DESCRIPTION_BLOCK = "description_block";
    private static final String MODAL = "modal";
    private static final String LOCATION_BLOCK = "location_block";
    private static final String PLAIN_TEXT = "plain_text";

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final SlackAuditLogService auditLogService;
    private final IncidentService incidentService;
    private final IncidentTypeRepository incidentTypeRepository;
    private final LocationRepository locationRepository;
    private final SeverityRepository severityRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final SlackProperties slackProperties;

    private static final String HILFE_WEB_URL = "https://hilfe.amalitech.net";

    public void openCreateIncidentModal(String slackUserId, String triggerId) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        List<IncidentType> types = incidentTypeRepository.findAll().stream()
                .filter(t -> "active".equalsIgnoreCase(t.getStatus()))
                .toList();
        List<Location> locations = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getStatus()))
                .toList();
        List<Severity> severities = severityRepository.findAll();

        View modal = buildCreateIncidentModal(types, locations, severities);
        slackClient.viewsOpen(triggerId, modal);

        auditLogService.log("INCIDENT_MODAL_OPENED", slackUserId, mapping.getHilfeUserId(),
                "MODAL", null, Map.of());
    }

    public void openMyIncidentsModal(String slackUserId, String triggerId) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        Page<IncidentResponse> incidents = incidentService.queryIncidents(
                mapping.getHilfeUserId(),
                null,
                new IncidentFilterParams(null, null, null, null, null),
                new IncidentDateFilter(null, false, null, false),
                PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        View modal = buildMyIncidentsModal(incidents.getContent());
        slackClient.viewsOpen(triggerId, modal);
    }

    public void openAssignedIncidentsModal(String slackUserId, String triggerId) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        User user = userRepository.findById(mapping.getHilfeUserId()).orElse(null);
        if (user == null || !isAgent(user)) {
            throw new SlackPermissionDeniedException("Only agents can view assigned incidents");
        }

        Page<IncidentResponse> incidents = incidentService.queryAssignedIncidents(
                mapping.getHilfeUserId(),
                null,
                new IncidentFilterParams(null, null, null, null, null),
                new IncidentDateFilter(null, false, null, false),
                PageRequest.of(0, 10, Sort.by("createdAt").descending())
        );

        View modal = buildAssignedIncidentsModal(incidents.getContent());
        slackClient.viewsOpen(triggerId, modal);
    }

    @Transactional
    public String handleCreateIncidentSubmission(JsonNode payload) {
        String slackUserId = payload.path("user").path("id").asText();

        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        JsonNode stateValues = payload.path("view").path("state").path("values");

        String title = extractTextValue(stateValues, TITLE_BLOCK, "title_input");
        String description = extractTextValue(stateValues, DESCRIPTION_BLOCK, "description_input");
        String incidentTypeId = extractSelectValue(stateValues, TYPE_BLOCK, "type_select");
        String locationId = extractSelectValue(stateValues, LOCATION_BLOCK, "location_select");
        String severityId = extractSelectValue(stateValues, "severity_block", "severity_select");

        Map<String, String> errors = validateIncidentData(title, description, incidentTypeId, locationId);
        if (!errors.isEmpty()) {
            return buildValidationErrorResponse(errors);
        }

        try {
            CreateIncidentRequest request = new CreateIncidentRequest(
                    title,
                    description,
                    incidentTypeId,
                    locationId,
                    severityId,
                    List.of()
            );

            IncidentResponse incident = incidentService.createIncident(mapping.getHilfeUserId(), request);

            slackClient.chatPostMessage(slackUserId,
                    ":white_check_mark: *Incident created successfully!*\n\n" +
                    "*ID:* " + incident.incidentNo() + "\n" +
                    "*Title:* " + incident.title() + "\n" +
                    "*Status:* " + incident.status().name() + "\n\n" +
                    "<" + HILFE_WEB_URL + "/incidents/" + incident.id() + "|View in HILFE>"
            );

            auditLogService.log(
                    "INCIDENT_CREATED_VIA_SLACK",
                    slackUserId,
                    mapping.getHilfeUserId(),
                    "INCIDENT",
                    incident.id(),
                    Map.of("incidentNo", incident.incidentNo())
            );

            return "";
        } catch (Exception e) {
            log.error("Failed to create incident via Slack", e);
            return buildValidationErrorResponse(Map.of(
                    TITLE_BLOCK, "Failed to create incident: " + e.getMessage()
            ));
        }
    }

    private View buildCreateIncidentModal(List<IncidentType> types, List<Location> locations, List<Severity> severities) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText("New incident"))));
        blocks.add(context(c -> c.elements(List.of(
                markdownText("Describe what happened and we'll route it to the right team.")
        ))));
        blocks.add(divider());

        blocks.add(input(i -> i
                .blockId(TITLE_BLOCK)
                .label(plainText("Title"))
                .element(plainTextInput(p -> p
                        .actionId("title_input")
                        .placeholder(plainText("Brief summary of the incident"))
                        .maxLength(100)
                ))
        ));

        blocks.add(input(i -> i
                .blockId(DESCRIPTION_BLOCK)
                .label(plainText("Description"))
                .element(plainTextInput(p -> p
                        .actionId("description_input")
                        .placeholder(plainText("What happened? Include any relevant details."))
                        .multiline(true)
                        .maxLength(1000)
                ))
        ));

        blocks.add(divider());
        blocks.add(section(s -> s.text(markdownText("*Details*"))));

        blocks.add(input(i -> i
                .blockId(TYPE_BLOCK)
                .label(plainText("Incident type"))
                .element(staticSelect(s -> s
                        .actionId("type_select")
                        .placeholder(plainText("Select type"))
                        .options(types.stream()
                                .map(t -> option(plainText(t.getName()), t.getId()))
                                .toList())
                ))
        ));

        Severity defaultSeverity = severities.stream()
                .filter(s -> "Low".equalsIgnoreCase(s.getName()))
                .findFirst()
                .orElse(severities.isEmpty() ? null : severities.get(0));

        blocks.add(input(i -> i
                .blockId("severity_block")
                .label(plainText("Severity"))
                .optional(true)
                .element(staticSelect(s -> {
                    var builder = s
                            .actionId("severity_select")
                            .placeholder(plainText("Select severity"))
                            .options(severities.stream()
                                    .map(sev -> option(plainText(sev.getName()), sev.getId()))
                                    .toList());
                    if (defaultSeverity != null) {
                        builder.initialOption(option(plainText(defaultSeverity.getName()), defaultSeverity.getId()));
                    }
                    return builder;
                }))
        ));

        blocks.add(input(i -> i
                .blockId(LOCATION_BLOCK)
                .label(plainText("Location"))
                .element(staticSelect(s -> s
                        .actionId("location_select")
                        .placeholder(plainText("Select location"))
                        .options(locations.stream()
                                .map(l -> option(plainText(l.getName()), l.getId()))
                                .toList())
                ))
        ));

        return View.builder()
                .type(MODAL)
                .callbackId("create_incident")
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("New incident").build())
                .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Create").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Cancel").build())
                .blocks(blocks)
                .build();
    }

    private View buildMyIncidentsModal(List<IncidentResponse> incidents) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText("My Incidents"))));
        blocks.add(divider());

        if (incidents.isEmpty()) {
            blocks.add(section(s -> s.text(markdownText(
                    "_You haven't created any incidents yet._\n\nUse `/hilfe new` to create one."
            ))));
        } else {
            for (IncidentResponse incident : incidents) {
                blocks.add(buildIncidentBlock(incident));
            }
        }

        return View.builder()
                .type(MODAL)
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("My Incidents").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Close").build())
                .blocks(blocks)
                .build();
    }

    private View buildAssignedIncidentsModal(List<IncidentResponse> incidents) {
        List<LayoutBlock> blocks = new ArrayList<>();

        blocks.add(header(h -> h.text(plainText("Assigned Incidents"))));
        blocks.add(divider());

        if (incidents.isEmpty()) {
            blocks.add(section(s -> s.text(markdownText(
                    "_You don't have any assigned incidents._"
            ))));
        } else {
            for (IncidentResponse incident : incidents) {
                blocks.add(buildIncidentBlock(incident));
            }
        }

        return View.builder()
                .type(MODAL)
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("Assigned").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Close").build())
                .blocks(blocks)
                .build();
    }

    private LayoutBlock buildIncidentBlock(IncidentResponse incident) {
        String statusName = incident.status() != null ? incident.status().name() : "Unknown";
        String statusEmoji = getStatusEmoji(statusName);
        String severityText = incident.priority() != null ? incident.priority().name() : "N/A";

        return section(s -> s
                .text(markdownText(
                        "*" + incident.incidentNo() + "* " + incident.title() + "\n" +
                        statusEmoji + " " + statusName + " · Priority: " + severityText + "\n" +
                        "<" + HILFE_WEB_URL + "/incidents/" + incident.id() + "|View in HILFE>"
                ))
        );
    }

    private String getStatusEmoji(String statusName) {
        return switch (statusName.toLowerCase()) {
            case "open" -> ":red_circle:";
            case "in progress" -> ":large_yellow_circle:";
            case "resolved" -> ":large_green_circle:";
            case "closed" -> ":black_circle:";
            default -> ":white_circle:";
        };
    }

    private Map<String, String> validateIncidentData(String title, String description,
                                                      String incidentTypeId, String locationId) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (title == null || title.isBlank()) {
            errors.put(TITLE_BLOCK, "Title is required");
        } else if (title.length() > 100) {
            errors.put(TITLE_BLOCK, "Title must not exceed 100 characters");
        }

        if (description == null || description.isBlank()) {
            errors.put(DESCRIPTION_BLOCK, "Description is required");
        } else if (description.length() > 1000) {
            errors.put(DESCRIPTION_BLOCK, "Description must not exceed 1000 characters");
        }

        if (incidentTypeId == null || incidentTypeId.isBlank()) {
            errors.put(TYPE_BLOCK, "Incident type is required");
        }

        if (locationId == null || locationId.isBlank()) {
            errors.put(LOCATION_BLOCK, "Location is required");
        }

        return errors;
    }

    private String buildValidationErrorResponse(Map<String, String> errors) {
        StringBuilder sb = new StringBuilder("{\"response_action\":\"errors\",\"errors\":{");
        boolean first = true;
        for (Map.Entry<String, String> entry : errors.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":\"")
                    .append(entry.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String extractTextValue(JsonNode stateValues, String blockId, String actionId) {
        return stateValues.path(blockId).path(actionId).path("value").asText(null);
    }

    private String extractSelectValue(JsonNode stateValues, String blockId, String actionId) {
        return stateValues.path(blockId).path(actionId).path("selected_option").path("value").asText(null);
    }

    private boolean isAgent(User user) {
        String roleCode = user.getRoleCode();
        return "AGENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode);
    }
}
