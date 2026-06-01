package com.amalitech.hilfe.auth;

import com.amalitech.hilfe.controllers.ActivityController;
import com.amalitech.hilfe.dto.ActivityLogResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.services.ActivityLogService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ActivityController.class)
@Import({GlobalExceptionHandler.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class ActivityControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ActivityLogService activityLogService;
    @MockitoBean TokenService tokenService;

    @Test
    void activityLogs_adminRequest_returnsPaginatedLogs() throws Exception {
        when(activityLogService.getActivityLogs(any())).thenReturn(new PageImpl<>(
                List.of(
                        new ActivityLogResponse(
                                1L,
                                "Jane Admin",
                                "John Doe",
                                "ROLE_CHANGED",
                                "USER",
                                null,
                                "Jane Admin changed role for John Doe from CLIENT to ADMIN",
                                "{\"previousRoleCode\":\"CLIENT\",\"newRoleCode\":\"ADMIN\"}",
                                Instant.parse("2026-05-06T08:00:00Z")
                        )
                ),
                PageRequest.of(0, 10),
                1
        ));

        var principal = new com.amalitech.hilfe.services.JwtTokenService.AuthPrincipal(
                "admin-1",
                "admin@test.com",
                RoleCode.ADMIN
        );

        mvc.perform(get("/activities")
                        .param("page", "0")
                        .param("size", "10")
                        .with(authentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                principal,
                                null,
                                List.of(() -> "ROLE_ADMIN")
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Activity logs retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].actorName").value("Jane Admin"))
                .andExpect(jsonPath("$.data.items[0].targetName").value("John Doe"))
                .andExpect(jsonPath("$.data.items[0].action").value("ROLE_CHANGED"))
                .andExpect(jsonPath("$.data.items[0].subjectType").value("USER"))
                .andExpect(jsonPath("$.data.items[0].subjectNo").doesNotExist())
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10));
    }
}
