package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.SeverityRequest;
import com.amalitech.hilfe.dto.SeverityResponse;
import com.amalitech.hilfe.services.SeverityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class SeverityResolver {

    private final SeverityService severityService;

    @QueryMapping
    public List<SeverityResponse> severities() {
        return severityService.listSeverities();
    }

    @QueryMapping
    public SeverityResponse severity(@Argument String id) {
        return severityService.getSeverity(id);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('severity.create')")
    public SeverityResponse createSeverity(@Valid @Argument SeverityRequest input) {
        GraphQlResponseMessage.set("Severity created successfully");
        return severityService.createSeverity(input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('severity.update')")
    public SeverityResponse updateSeverity(@Argument String id, @Valid @Argument SeverityRequest input) {
        GraphQlResponseMessage.set("Severity updated successfully");
        return severityService.updateSeverity(id, input);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('severity.update')")
    public SeverityResponse updateSeveritySla(@Argument String id, @Valid @Argument UpdateSeveritySlaInput input) {
        GraphQlResponseMessage.set("Severity SLA updated successfully");
        return severityService.updateSeveritySla(id, input.toRequest());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('severity.delete')")
    public SeverityResponse deactivateSeverity(@Argument String id) {
        GraphQlResponseMessage.set("Severity deactivated successfully");
        return severityService.deactivateSeverity(id);
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('severity.delete')")
    public boolean deleteSeverity(@Argument String id) {
        severityService.deleteSeverity(id);
        GraphQlResponseMessage.set("Severity deleted successfully");
        return true;
    }

    public record UpdateSeveritySlaInput(Long responseTimeSeconds, Long resolutionTimeSeconds) {
        public com.amalitech.hilfe.dto.UpdateSeveritySlaRequest toRequest() {
            return new com.amalitech.hilfe.dto.UpdateSeveritySlaRequest(
                    responseTimeSeconds != null ? responseTimeSeconds.intValue() : null,
                    resolutionTimeSeconds != null ? resolutionTimeSeconds.intValue() : null
            );
        }
    }
}
