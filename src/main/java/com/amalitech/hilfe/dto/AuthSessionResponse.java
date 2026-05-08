package com.amalitech.hilfe.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthSessionResponse {
    private String userId;
    private String email;
    private String fullName;
    private String profileImg;
    private List<String> permissions;
}
