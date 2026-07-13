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

    private static final String TITLE_BLOCK       = "title_block";
    private static final String CATEGORY_BLOCK    = "category_block";
    private static final String TOPIC_BLOCK       = "topic_block";
    private static final String LOCATION_BLOCK    = "location_block";
    private static final String VALUE_FIELD        = "value";
    private static final String SELECTED_OPTION    = "selected_option";
    private static final String ACTION_TITLE       = "title_input";
    private static final String ACTION_DESCRIPTION = "description_input";
    private static final String ACTION_CATEGORY    = "category_select";
    private static final String ACTION_TOPIC       = "topic_select";
    private static final String ACTION_LOCATION    = "location_select";
    private static final String ACTION_SEVERITY    = "severity_select";
    private static final String SEVERITY_BLOCK     = "severity_block";
    private static final String DESCRIPTION_BLOCK = "description_block";
    private static final String MODAL             = "modal";
    private static final String PLAIN_TEXT        = "plain_text";

    private static final String HILFE_WEB_URL     = "https://hilfe-pro-frontend.amalitech-dev.net";
    private static final String EMOJI_NONE_CIRCLE = ":white_circle:";

    private record ModalState(
            String title,
            String description,
            String locationId,
            String locationName,
            String categoryId,
            String categoryName,
            String severityId,
            String severityName
    ) {
        static ModalState empty() {
            return new ModalState(null, null, null, null, null, null, null, null);
        }
    }

    private final SlackClient slackClient;
    private final SlackOAuthService oauthService;
    private final SlackAuditLogService auditLogService;
    private final IncidentService incidentService;
    private final IncidentCategoryRepository incidentCategoryRepository;
    private final IncidentTypeRepository incidentTypeRepository;
    private final LocationRepository locationRepository;
    private final SeverityRepository severityRepository;
    private final IncidentRepository incidentRepository;
    private final UserRepository userRepository;
    private final SlackProperties slackProperties;

    // ── Open modals ──────────────────────────────────────────────────────────

    public void openCreateIncidentModal(String slackUserId, String triggerId) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        List<IncidentCategory> categories = incidentCategoryRepository.findByStatus(true);
        List<Location> locations = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getStatus()))
                .toList();
        List<Severity> severities = severityRepository.findByStatus(true);

        View modal = buildCreateIncidentModal(categories, locations, List.of(), severities, ModalState.empty());
        slackClient.viewsOpen(triggerId, modal);

        auditLogService.log("INCIDENT_MODAL_OPENED", slackUserId, mapping.getHilfeUserId(),
                "MODAL", null, Map.of());
    }

    public void openMyIncidentsModal(String slackUserId, String triggerId) {
        openMyIncidentsModal(slackUserId, triggerId, null, 0);
    }

    public void openMyIncidentsModal(String slackUserId, String triggerId, String viewId, int page) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        Page<IncidentResponse> incidents = incidentService.queryIncidents(
                mapping.getHilfeUserId(), null,
                new IncidentFilterParams(null, null, null, null, null),
                new IncidentDateFilter(null, false, null, false),
                PageRequest.of(page, 10, Sort.by("createdAt").descending())
        );

        View modal = buildMyIncidentsModal(incidents.getContent(), page, incidents.getTotalElements());
        if (viewId != null) {
            slackClient.viewsUpdate(viewId, modal);
        } else {
            slackClient.viewsOpen(triggerId, modal);
        }
    }

    public void openAssignedIncidentsModal(String slackUserId, String triggerId) {
        openAssignedIncidentsModal(slackUserId, triggerId, null, 0);
    }

    public void openAssignedIncidentsModal(String slackUserId, String triggerId, String viewId, int page) {
        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        User user = userRepository.findById(mapping.getHilfeUserId()).orElse(null);
        if (user == null || !isAgent(user)) {
            throw new SlackPermissionDeniedException("Only agents can view assigned incidents");
        }

        Page<IncidentResponse> incidents = incidentService.queryAssignedIncidents(
                mapping.getHilfeUserId(), null,
                new IncidentFilterParams(null, null, null, null, null),
                new IncidentDateFilter(null, false, null, false),
                PageRequest.of(page, 10, Sort.by("createdAt").descending())
        );

        View modal = buildAssignedIncidentsModal(incidents.getContent(), page, incidents.getTotalElements());
        if (viewId != null) {
            slackClient.viewsUpdate(viewId, modal);
        } else {
            slackClient.viewsOpen(triggerId, modal);
        }
    }

    // ── Category selection — rebuild modal with topics ───────────────────────

    public void handleCategorySelection(JsonNode payload) {
        JsonNode action = payload.path("actions").get(0);
        String categoryId   = action.path(SELECTED_OPTION).path(VALUE_FIELD).asText(null);
        String categoryName = action.path(SELECTED_OPTION).path("text").path("text").asText(null);
        String viewId       = payload.path("view").path("id").asText();

        JsonNode stateValues = payload.path("view").path("state").path("values");

        String currentTitle        = extractTextValue(stateValues, TITLE_BLOCK, ACTION_TITLE);
        String currentDescription  = extractTextValue(stateValues, DESCRIPTION_BLOCK, ACTION_DESCRIPTION);
        String currentLocationId   = extractSelectValue(stateValues, LOCATION_BLOCK, ACTION_LOCATION);
        String currentLocationName = stateValues.path(LOCATION_BLOCK).path(ACTION_LOCATION)
                .path(SELECTED_OPTION).path("text").path("text").asText(null);
        String currentSeverityId   = extractSelectValue(stateValues, SEVERITY_BLOCK, ACTION_SEVERITY);
        String currentSeverityName = stateValues.path(SEVERITY_BLOCK).path(ACTION_SEVERITY)
                .path(SELECTED_OPTION).path("text").path("text").asText(null);

        List<IncidentCategory> categories = incidentCategoryRepository.findByStatus(true);
        List<Location> locations = locationRepository.findAll().stream()
                .filter(l -> Boolean.TRUE.equals(l.getStatus()))
                .toList();
        List<Severity> severities = severityRepository.findByStatus(true);
        List<IncidentType> topics = categoryId != null
                ? incidentTypeRepository.findByCategoryIdWithAgentAndStatus(categoryId, true)
                : List.of();

        ModalState state = new ModalState(
                currentTitle, currentDescription,
                currentLocationId, currentLocationName,
                categoryId, categoryName,
                currentSeverityId, currentSeverityName);
        View updated = buildCreateIncidentModal(categories, locations, topics, severities, state);

        slackClient.viewsUpdate(viewId, updated);
        log.debug("Modal updated for category selection: {}", categoryId);
    }

    // ── Submission ───────────────────────────────────────────────────────────

    @Transactional
    public String handleCreateIncidentSubmission(JsonNode payload) {
        String slackUserId = payload.path("user").path("id").asText();

        SlackUserMapping mapping = oauthService.findBySlackUserId(slackUserId)
                .orElseThrow(SlackNotConnectedException::new);

        JsonNode stateValues = payload.path("view").path("state").path("values");

        String title          = extractTextValue(stateValues, TITLE_BLOCK, ACTION_TITLE);
        String description    = extractTextValue(stateValues, DESCRIPTION_BLOCK, ACTION_DESCRIPTION);
        String categoryId     = extractSelectValue(stateValues, CATEGORY_BLOCK, ACTION_CATEGORY);
        String incidentTypeId = extractSelectValue(stateValues, TOPIC_BLOCK, ACTION_TOPIC);
        String locationId     = extractSelectValue(stateValues, LOCATION_BLOCK, ACTION_LOCATION);
        String severityId     = extractSelectValue(stateValues, SEVERITY_BLOCK, ACTION_SEVERITY);

        Map<String, String> errors = validateSubmission(title, description, categoryId, incidentTypeId, locationId);
        if (!errors.isEmpty()) return buildValidationErrorResponse(errors);

        try {
            CreateIncidentRequest request = new CreateIncidentRequest(
                    title, description, incidentTypeId, locationId, severityId, List.of());

            IncidentResponse incident = incidentService.createIncident(mapping.getHilfeUserId(), request);

            slackClient.chatPostMessage(slackUserId,
                    ":white_check_mark: *Incident created successfully!*\n\n" +
                    "*ID:* " + incident.incidentNo() + "\n" +
                    "*Title:* " + incident.title() + "\n" +
                    "*Status:* " + incident.status().name() + "\n\n" +
                    "<" + HILFE_WEB_URL + "/incidents/" + incident.id() + "|View in HILFE>");

            auditLogService.log("INCIDENT_CREATED_VIA_SLACK", slackUserId, mapping.getHilfeUserId(),
                    "INCIDENT", incident.id(), Map.of("incidentNo", incident.incidentNo()));

            return "";
        } catch (Exception e) {
            log.error("Failed to create incident via Slack", e);
            return buildValidationErrorResponse(Map.of(TITLE_BLOCK, "Failed to create incident: " + e.getMessage()));
        }
    }

    // ── Modal builders ───────────────────────────────────────────────────────

    private View buildCreateIncidentModal(List<IncidentCategory> categories,
                                          List<Location> locations,
                                          List<IncidentType> topics,
                                          List<Severity> severities,
                                          ModalState state) {
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(buildTitleBlock(state.title()));
        blocks.add(buildCategoryBlock(categories, state.categoryId(), state.categoryName()));
        blocks.add(buildTopicBlock(state.categoryId(), topics));
        blocks.add(buildLocationBlock(locations, state.locationId(), state.locationName()));
        blocks.add(buildSeverityBlock(severities, state.severityId(), state.severityName()));
        blocks.add(buildDescriptionBlock(state.description()));

        return View.builder()
                .type(MODAL)
                .callbackId("create_incident")
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("New Incident").build())
                .submit(ViewSubmit.builder().type(PLAIN_TEXT).text("Submit Incident").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Cancel").build())
                .blocks(blocks)
                .build();
    }

    private LayoutBlock buildTitleBlock(String currentTitle) {
        return input(i -> i
                .blockId(TITLE_BLOCK)
                .label(plainText("Title"))
                .element(plainTextInput(p -> {
                    var b = p.actionId(ACTION_TITLE)
                            .placeholder(plainText("Brief summary of the incident"))
                            .maxLength(100);
                    if (currentTitle != null && !currentTitle.isBlank()) b.initialValue(currentTitle);
                    return b;
                }))
        );
    }

    private LayoutBlock buildCategoryBlock(List<IncidentCategory> categories,
                                            String selectedCategoryId, String selectedCategoryName) {
        return input(i -> i
                .blockId(CATEGORY_BLOCK)
                .dispatchAction(true)
                .label(plainText("Incident Category"))
                .element(staticSelect(s -> {
                    var b = s.actionId(ACTION_CATEGORY)
                            .placeholder(plainText("Select category"))
                            .options(categories.stream()
                                    .map(c -> option(plainText(c.getName()), c.getId()))
                                    .toList());
                    if (selectedCategoryId != null && selectedCategoryName != null) {
                        b.initialOption(option(plainText(selectedCategoryName), selectedCategoryId));
                    }
                    return b;
                }))
        );
    }

    private LayoutBlock buildTopicBlock(String selectedCategoryId, List<IncidentType> topics) {
        if (selectedCategoryId != null && !topics.isEmpty()) {
            return input(i -> i
                    .blockId(TOPIC_BLOCK)
                    .label(plainText("Incident Topic"))
                    .element(staticSelect(s -> s
                            .actionId(ACTION_TOPIC)
                            .placeholder(plainText("Select topic"))
                            .options(topics.stream()
                                    .map(t -> option(plainText(t.getName()), t.getId()))
                                    .toList())
                    ))
            );
        }
        String hint = selectedCategoryId != null
                ? "_No active topics found for this category._"
                : "_Select a category to load incident topics._";
        return context(c -> c.elements(List.of(markdownText(hint))));
    }

    private LayoutBlock buildLocationBlock(List<Location> locations,
                                            String currentLocationId, String currentLocationName) {
        return input(i -> i
                .blockId(LOCATION_BLOCK)
                .label(plainText("Location"))
                .element(staticSelect(s -> {
                    var b = s.actionId(ACTION_LOCATION)
                            .placeholder(plainText("Select location"))
                            .options(locations.stream()
                                    .map(l -> option(plainText(l.getName()), l.getId()))
                                    .toList());
                    if (currentLocationId != null && currentLocationName != null) {
                        b.initialOption(option(plainText(currentLocationName), currentLocationId));
                    }
                    return b;
                }))
        );
    }

    private LayoutBlock buildSeverityBlock(List<Severity> severities,
                                            String currentSeverityId, String currentSeverityName) {
        return input(i -> i
                .blockId(SEVERITY_BLOCK)
                .optional(true)
                .label(plainText("Priority"))
                .element(staticSelect(s -> {
                    var b = s.actionId(ACTION_SEVERITY)
                            .placeholder(plainText("Select priority (optional)"))
                            .options(severities.stream()
                                    .map(sv -> option(plainText(sv.getName()), sv.getId()))
                                    .toList());
                    if (currentSeverityId != null && currentSeverityName != null) {
                        b.initialOption(option(plainText(currentSeverityName), currentSeverityId));
                    }
                    return b;
                }))
        );
    }

    private LayoutBlock buildDescriptionBlock(String currentDescription) {
        return input(i -> i
                .blockId(DESCRIPTION_BLOCK)
                .label(plainText("Description"))
                .element(plainTextInput(p -> {
                    var b = p.actionId(ACTION_DESCRIPTION)
                            .placeholder(plainText("Describe the incident in detail..."))
                            .maxLength(1000);
                    if (currentDescription != null && !currentDescription.isBlank()) b.initialValue(currentDescription);
                    return b;
                }))
        );
    }

    private View buildMyIncidentsModal(List<IncidentResponse> incidents, int page, long totalElements) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / 10));
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(header(h -> h.text(plainText("My Incidents"))));
        blocks.add(divider());

        if (incidents.isEmpty()) {
            blocks.add(section(s -> s.text(markdownText(
                    "_You haven't created any incidents yet._\n\nUse `/hilfe new` to create one."))));
        } else {
            for (IncidentResponse incident : incidents) {
                blocks.addAll(buildIncidentBlock(incident));
            }
        }

        addPaginationBlocks(blocks, page, totalPages, totalElements, "my_incidents_prev", "my_incidents_next");

        return View.builder()
                .type(MODAL)
                .privateMetadata("{\"page\":" + page + ",\"type\":\"MY\"}")
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("My Incidents").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Close").build())
                .blocks(blocks)
                .build();
    }

    private View buildAssignedIncidentsModal(List<IncidentResponse> incidents, int page, long totalElements) {
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / 10));
        List<LayoutBlock> blocks = new ArrayList<>();
        blocks.add(header(h -> h.text(plainText("Assigned Incidents"))));
        blocks.add(divider());

        if (incidents.isEmpty()) {
            blocks.add(section(s -> s.text(markdownText("_You don't have any assigned incidents._"))));
        } else {
            for (IncidentResponse incident : incidents) {
                blocks.addAll(buildIncidentBlock(incident));
            }
        }

        addPaginationBlocks(blocks, page, totalPages, totalElements, "assigned_incidents_prev", "assigned_incidents_next");

        return View.builder()
                .type(MODAL)
                .privateMetadata("{\"page\":" + page + ",\"type\":\"ASSIGNED\"}")
                .title(ViewTitle.builder().type(PLAIN_TEXT).text("Assigned").build())
                .close(ViewClose.builder().type(PLAIN_TEXT).text("Close").build())
                .blocks(blocks)
                .build();
    }

    private void addPaginationBlocks(List<LayoutBlock> blocks, int page, int totalPages,
                                     long totalElements, String prevActionId, String nextActionId) {
        if (totalElements == 0) return;

        if (totalPages > 1) {
            List<com.slack.api.model.block.element.BlockElement> navButtons = new ArrayList<>();
            if (page > 0) {
                int prevPage = page - 1;
                navButtons.add(button(b -> b.text(plainText("← Previous"))
                        .actionId(prevActionId)
                        .value(String.valueOf(prevPage))));
            }
            if (page < totalPages - 1) {
                int nextPage = page + 1;
                navButtons.add(button(b -> b.text(plainText("Next →"))
                        .actionId(nextActionId)
                        .value(String.valueOf(nextPage))));
            }
            if (!navButtons.isEmpty()) {
                blocks.add(actions(a -> a.elements(navButtons)));
            }
            long from = (long) page * 10 + 1;
            long to   = Math.min((long)(page + 1) * 10, totalElements);
            String pageInfo = "Showing *" + from + "* to *" + to + "* of *" + totalElements + "* Incidents";
            blocks.add(context(c -> c.elements(List.of(markdownText(pageInfo)))));
        }
    }

    private List<LayoutBlock> buildIncidentBlock(IncidentResponse incident) {
        String statusName   = incident.status()   != null ? incident.status().name()   : "Unknown";
        String priorityName = incident.priority() != null ? incident.priority().name() : "N/A";
        String url          = HILFE_WEB_URL + "/incidents/" + incident.id();

        StringBuilder text = new StringBuilder();
        text.append("*#").append(incident.incidentNo()).append("  ").append(incident.title()).append("*\n");
        text.append(getStatusEmoji(statusName)).append(" ").append(statusName);
        text.append("   ").append(getPriorityEmoji(priorityName)).append(" ").append(priorityName);
        if (incident.assignedTo() != null && incident.assignedTo().fullName() != null) {
            text.append("   ·   Assigned to ").append(incident.assignedTo().fullName());
        }

        LayoutBlock incidentSection = section(s -> s
                .text(markdownText(text.toString()))
                .accessory(button(b -> b
                        .text(plainText("Open"))
                        .url(url)
                        .actionId("open_incident_" + incident.id()))));

        return List.of(incidentSection);
    }

    private String getStatusEmoji(String statusName) {
        return switch (statusName.toLowerCase()) {
            case "open"        -> ":large_green_circle:";
            case "in progress" -> ":large_yellow_circle:";
            case "resolved"    -> ":large_blue_circle:";
            case "closed"      -> EMOJI_NONE_CIRCLE;
            default            -> EMOJI_NONE_CIRCLE;
        };
    }

    private String getPriorityEmoji(String priority) {
        if (priority == null) return EMOJI_NONE_CIRCLE;
        return switch (priority.toLowerCase()) {
            case "critical"            -> ":red_circle:";
            case "high"                -> ":large_orange_circle:";
            case "medium", "moderate"  -> ":large_yellow_circle:";
            case "low"                 -> ":large_blue_circle:";
            default                    -> EMOJI_NONE_CIRCLE;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, String> validateSubmission(String title, String description,
                                                    String categoryId, String topicId, String locationId) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (title == null || title.isBlank())
            errors.put(TITLE_BLOCK, "Title is required");
        else if (title.length() > 100)
            errors.put(TITLE_BLOCK, "Title must not exceed 100 characters");

        if (categoryId == null || categoryId.isBlank())
            errors.put(CATEGORY_BLOCK, "Incident category is required");

        if (topicId == null || topicId.isBlank())
            errors.put(CATEGORY_BLOCK, "Please select a category and then choose a topic");

        if (locationId == null || locationId.isBlank())
            errors.put(LOCATION_BLOCK, "Location is required");

        if (description == null || description.isBlank())
            errors.put(DESCRIPTION_BLOCK, "Description is required");
        else if (description.length() > 1000)
            errors.put(DESCRIPTION_BLOCK, "Description must not exceed 1000 characters");

        return errors;
    }

    private String buildValidationErrorResponse(Map<String, String> errors) {
        StringBuilder sb = new StringBuilder("{\"response_action\":\"errors\",\"errors\":{");
        boolean first = true;
        for (Map.Entry<String, String> e : errors.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(e.getKey()).append("\":\"")
              .append(e.getValue().replace("\"", "\\\"")).append("\"");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    private String extractTextValue(JsonNode stateValues, String blockId, String actionId) {
        return stateValues.path(blockId).path(actionId).path(VALUE_FIELD).asText(null);
    }

    private String extractSelectValue(JsonNode stateValues, String blockId, String actionId) {
        return stateValues.path(blockId).path(actionId).path(SELECTED_OPTION).path(VALUE_FIELD).asText(null);
    }

    private boolean isAgent(User user) {
        String roleCode = user.getRoleCode();
        return "AGENT".equalsIgnoreCase(roleCode) || "ADMIN".equalsIgnoreCase(roleCode) || "ADMIN_AGENT".equalsIgnoreCase(roleCode);
    }
}
