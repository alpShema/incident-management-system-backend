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
    void armsAuthException401_returns401WithDetail() throws Exception {
        mvc.perform(get("/throw/arms-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid ARMS token"));
    }

    @Test
    void armsAuthException502_returns502WithDetail() throws Exception {
        mvc.perform(get("/throw/arms-502"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("ARMS unavailable"));
    }

    @Test
    void unsupportedOperation_returns501() throws Exception {
        mvc.perform(get("/throw/not-implemented"))
                .andExpect(status().isNotImplemented());
    }

    @Test
    void genericException_returns500() throws Exception {
        mvc.perform(get("/throw/generic"))
                .andExpect(status().isInternalServerError());
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
