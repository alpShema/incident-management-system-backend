package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.RoleCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionResponse {
    private String userId;
    private String email;
    private String fullName;
    private String profileImg;
    private RoleCode roleCode;
}
