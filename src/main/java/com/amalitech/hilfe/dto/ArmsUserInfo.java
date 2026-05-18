package com.amalitech.hilfe.dto;

public record ArmsUserInfo(
        String userId,
        String firstName,
        String lastName,
        String otherName,
        String email,
        String profileImage,
        String positionName,
        String officeName,
        String workEmail,
        String personalEmail,
        String phoneNumber
) {
    public ArmsUserInfo(
            String userId,
            String firstName,
            String lastName,
            String email,
            String profileImage,
            String locationTown
    ) {
        this(userId, firstName, lastName, null, email, profileImage, null, locationTown, null, email, null);
    }
}
