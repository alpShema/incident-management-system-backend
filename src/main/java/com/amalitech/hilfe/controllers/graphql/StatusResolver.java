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

    @QueryMapping
    public List<StatusLookupResponse> statuses() {
        return statusService.listStatuses();
    }
}
