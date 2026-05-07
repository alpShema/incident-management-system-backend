package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;

public record UserRoleSummaryResponse(
        String userId,
        String email,
        String fullName,
        String profileImg,
        RoleCode roleCode
) {
}
