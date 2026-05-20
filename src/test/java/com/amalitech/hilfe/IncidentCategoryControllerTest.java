package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentCategoryController;
import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.dto.LookupResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.models.RoleCode;
import com.amalitech.hilfe.services.IncidentCategoryService;
import com.amalitech.hilfe.services.JwtTokenService;
import com.amalitech.hilfe.services.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentCategoryController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class IncidentCategoryControllerTest {

    @Autowired MockMvc mvc;
    final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean IncidentCategoryService categoryService;
    @MockitoBean TokenService tokenService;

    private JwtTokenService.AuthPrincipal adminPrincipal() {
        return new JwtTokenService.AuthPrincipal("admin-1", "admin@test.com", RoleCode.ADMIN);
    }

    private JwtTokenService.AuthPrincipal agentPrincipal() {
        return new JwtTokenService.AuthPrincipal("agent-user-1", "agent@test.com", RoleCode.AGENT);
    }

    private IncidentCategoryResponse stubCategory() {
        return new IncidentCategoryResponse("cat-1", "Facility", "Facility incidents", "active");
    }

    private IncidentTopicResponse stubTopic() {
        return new IncidentTopicResponse("type-1", "Projector", "Projector issues", null, new LookupResponse("agent-1", "Agent One"));
    }

    // ── GET /incident-categories ──────────────────────────────────────────────

    @Test
    void listCategories_returns200WithList() throws Exception {
        when(categoryService.listCategories()).thenReturn(List.of(stubCategory()));

        mvc.perform(get("/incident-categories")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident categories retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value("cat-1"))
                .andExpect(jsonPath("$.data[0].name").value("Facility"));
    }

    // ── POST /incident-categories ─────────────────────────────────────────────

    @Test
    void createCategory_validRequest_returns201() throws Exception {
        when(categoryService.createCategory(any())).thenReturn(stubCategory());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.create"));

        mvc.perform(post("/incident-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IncidentCategoryRequest("Facility", "Facility incidents")))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident category created successfully"))
                .andExpect(jsonPath("$.data.id").value("cat-1"));
    }

    @Test
    void createCategory_duplicateName_returns409() throws Exception {
        when(categoryService.createCategory(any()))
                .thenThrow(new ArmsAuthException("Incident category with this name already exists", 409));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.create"));

        mvc.perform(post("/incident-categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IncidentCategoryRequest("Facility", "Duplicate")))
                        .with(authentication(auth)))
                .andExpect(status().isConflict());
    }

    // ── PATCH /incident-categories/{id} ──────────────────────────────────────

    @Test
    void updateCategory_validRequest_returns200() throws Exception {
        when(categoryService.updateCategory(eq("cat-1"), any())).thenReturn(stubCategory());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.update"));

        mvc.perform(patch("/incident-categories/cat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new IncidentCategoryRequest("Updated", "Updated description")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident category updated successfully"));
    }

    // ── DELETE /incident-categories/{id} ─────────────────────────────────────

    @Test
    void deleteCategory_validRequest_returns204() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.delete"));

        mvc.perform(delete("/incident-categories/cat-1")
                        .with(authentication(auth)))
                .andExpect(status().isNoContent());
    }

    // ── GET /incident-categories/{id}/topics ─────────────────────────────────

    @Test
    void listTopics_returns200() throws Exception {
        when(categoryService.listTopicsByCategory("cat-1")).thenReturn(List.of(stubTopic()));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN"));

        mvc.perform(get("/incident-categories/cat-1/topics")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident topics retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value("type-1"));
    }

    // ── POST /incident-categories/{id}/topics ─────────────────────────────────

    @Test
    void createTopic_validRequest_returns201AndUsesAuthenticatedUser() throws Exception {
        when(categoryService.createTopic(any(), any(), any(CreateTopicRequest.class)))
                .thenReturn(stubTopic());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-type.create"));

        mvc.perform(post("/incident-categories/cat-1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident topic created successfully"))
                .andExpect(jsonPath("$.data.id").value("type-1"));

        verify(categoryService).createTopic(eq("cat-1"), any(), any(CreateTopicRequest.class));
    }

    @Test
    void createTopic_agentWithPermission_returns201AndUsesAuthenticatedUser() throws Exception {
        when(categoryService.createTopic(any(), any(), any(CreateTopicRequest.class)))
                .thenReturn(stubTopic());

        var auth = new UsernamePasswordAuthenticationToken(
                agentPrincipal(), null, List.of(() -> "incident-type.create"));

        mvc.perform(post("/incident-categories/cat-1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident topic created successfully"))
                .andExpect(jsonPath("$.data.id").value("type-1"));

        verify(categoryService).createTopic(eq("cat-1"), any(), any(CreateTopicRequest.class));
    }

    @Test
    void createTopic_userWithoutAgent_returns403() throws Exception {
        when(categoryService.createTopic(any(), any(), any(CreateTopicRequest.class)))
                .thenThrow(new ArmsAuthException("Authenticated user is not linked to an agent record", 403));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-type.create"));

        mvc.perform(post("/incident-categories/cat-1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                        .with(authentication(auth)))
                .andExpect(status().isForbidden());
    }

}
