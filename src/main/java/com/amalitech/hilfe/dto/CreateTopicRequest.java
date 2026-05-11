package com.amalitech.hilfe.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTopicRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String agentId,
        boolean visibleToGroup
) {}
