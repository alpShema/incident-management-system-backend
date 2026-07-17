package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.config.GraphQlResponseMessage;
import com.amalitech.hilfe.dto.AutoCloseConfigResponse;
import com.amalitech.hilfe.dto.SlaConfigResponse;
import com.amalitech.hilfe.dto.UpdateAutoCloseConfigRequest;
import com.amalitech.hilfe.dto.UpdateSlaConfigRequest;
import com.amalitech.hilfe.services.AutoCloseService;
import com.amalitech.hilfe.services.SlaService;
import lombok.RequiredArgsConstructor;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class SystemConfigResolver {

    private final AutoCloseService autoCloseService;
    private final SlaService slaService;

    @QueryMapping
    @PreAuthorize("hasAuthority('system.config.read')")
    public AutoCloseConfigResponse autoCloseConfig() {
        return autoCloseService.getConfig();
    }

    @QueryMapping
    @PreAuthorize("hasAuthority('system.config.read')")
    public SlaConfigResponse slaConfig() {
        return slaService.getConfig();
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('system.config.update')")
    public AutoCloseConfigResponse updateAutoCloseConfig(@Argument UpdateAutoCloseConfigRequest input) {
        GraphQlResponseMessage.set("Auto-close configuration updated successfully");
        return autoCloseService.updateConfig(input.durationSeconds());
    }

    @MutationMapping
    @PreAuthorize("hasAuthority('system.config.update')")
    public SlaConfigResponse updateSlaConfig(@Argument UpdateSlaConfigRequest input) {
        GraphQlResponseMessage.set("SLA configuration updated successfully");
        return slaService.updateConfig(input);
    }
}
