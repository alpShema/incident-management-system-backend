package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.services.StatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class StatusResolver {

    private final StatusService statusService;

    // No authority required, matching the other reference-data lookups (severities,
    // timezones, ...): status names/descriptions aren't sensitive, and role is no longer
    // used to filter this list (see HV-1653 -- Unassigned must always be included).
    @QueryMapping
    public List<StatusLookupResponse> statuses() {
        return statusService.listStatuses();
    }
}
