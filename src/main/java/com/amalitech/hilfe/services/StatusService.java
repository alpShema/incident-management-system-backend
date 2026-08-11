package com.amalitech.hilfe.services;

import com.amalitech.hilfe.dto.StatusLookupResponse;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.repositories.StatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StatusService {
    private static final String STATUS_UNASSIGNED = "status-unassigned";

    private final StatusRepository statusRepository;

    // Agents never encounter an Unassigned incident (every agent-facing query requires a
    // non-null assignee), so it's excluded from their filter dropdown. CLIENT keeps it -- a
    // reporter needs to be able to filter their own unpicked-up incident -- as do all
    // admin-level roles. A null role fails open (kept in the list) rather than hiding it.
    public List<StatusLookupResponse> listStatuses(RoleCode role) {
        return statusRepository.findAll().stream()
                .filter(s -> role != RoleCode.AGENT || !STATUS_UNASSIGNED.equals(s.getId()))
                .map(s -> StatusLookupResponse.from(s.getId(), s.getName(), s.getDescription()))
                .toList();
    }
}
