package com.amalitech.hilfe.exceptions;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders
                .standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void armsAuthException401_returns401WithStandardPayload() throws Exception {
        mvc.perform(get("/throw/arms-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid ARMS token"))
                .andExpect(jsonPath("$.path").value("/throw/arms-401"));
    }

    @Test
    void armsAuthException502_returns502WithStandardPayload() throws Exception {
        mvc.perform(get("/throw/arms-502"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.error").value("Bad Gateway"))
                .andExpect(jsonPath("$.message").value("ARMS unavailable"))
                .andExpect(jsonPath("$.path").value("/throw/arms-502"));
    }

    @Test
    void unsupportedOperation_returns501() throws Exception {
        mvc.perform(get("/throw/not-implemented"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.error").value("Not Implemented"))
                .andExpect(jsonPath("$.message").value("Not yet implemented"))
                .andExpect(jsonPath("$.path").value("/throw/not-implemented"));
    }

    @Test
    void genericException_returns500() throws Exception {
        mvc.perform(get("/throw/generic"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.error").value("Internal Server Error"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.path").value("/throw/generic"));
    }

    @RestController
    static class ThrowingController {

        @GetMapping("/throw/arms-401")
        void throwArms401() {
            throw new ArmsAuthException("Invalid ARMS token", 401);
        }

        @GetMapping("/throw/arms-502")
        void throwArms502() {
            throw new ArmsAuthException("ARMS unavailable", 502);
        }

        @GetMapping("/throw/not-implemented")
        void throwNotImplemented() {
            throw new UnsupportedOperationException("Not yet implemented");
        }

        @GetMapping("/throw/generic")
        void throwGeneric() {
            throw new RuntimeException("Something went wrong");
        }
    }
}
