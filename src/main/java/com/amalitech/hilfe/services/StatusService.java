package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.repositories.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusService {

    private final StatusRepository statusRepository;

    // Returns the complete, role-agnostic set of statuses configured in the system (see
    // HV-1653). "Unassigned" used to be dropped for the AGENT role on the theory that agents
    // never encounter an unassigned incident directly -- true for agent-facing incident
    // queries, but this lookup also backs status *filter* dropdowns, where an agent legitimately
    // wants to filter a shared/team view down to unassigned incidents. No role should special-case
    // a value out of this list; it must always mirror StatusRepository exactly.
    public List<StatusLookupResponse> listStatuses() {
        return statusRepository.findAll().stream()
                .map(s -> StatusLookupResponse.from(s.getId(), s.getName(), s.getDescription()))
                .toList();
    }
}
