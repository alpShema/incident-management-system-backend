package com.amalitech.hilfe.dto;

public record LookupResponse(String id, String name) {
    public static LookupResponse from(String id, String name) {
        return new LookupResponse(id, name);
    }
}
