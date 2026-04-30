package com.amalitech.hilfe.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ArmsEmployeeInfo(
        @JsonProperty("user_id")       String userId,
        @JsonProperty("full_name")     String fullName,
        @JsonProperty("position")      String position,
        @JsonProperty("email")         String email,
        @JsonProperty("active")        boolean active,
        @JsonProperty("location")      String location,
        @JsonProperty("profile_image") String profileImage

) {}
