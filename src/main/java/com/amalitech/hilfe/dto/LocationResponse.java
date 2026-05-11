package com.amalitech.hilfe.dto;

import com.amalitech.hilfe.models.Location;

public record LocationResponse(String id, String name) {
    public static LocationResponse from(Location location) {
        return new LocationResponse(location.getId(), location.getName());
    }
}
