package com.amalitech.hilfe.config;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Lets a GraphQL resolver attach a specific success message (e.g. "Agent's
 * availability updated successfully") that {@link GraphQlResponseFilter} surfaces
 * in the response envelope, instead of the generic "Success" fallback.
 */
public final class GraphQlResponseMessage {

    static final String ATTRIBUTE_NAME = "graphql.response.message";

    private GraphQlResponseMessage() {
    }

    public static void set(String message) {
        RequestContextHolder.currentRequestAttributes()
                .setAttribute(ATTRIBUTE_NAME, message, RequestAttributes.SCOPE_REQUEST);
    }
}
