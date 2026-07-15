package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.IncidentCategoryController;
import com.amalitech.hilfe.dto.*;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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

    private IncidentCategoryResponse stubCategory() {
        return new IncidentCategoryResponse("cat-1", "Facility", "Facility incidents", null, true, null);
    }

    private IncidentTopicResponse stubTopic() {
        return new IncidentTopicResponse(
                "type-1",
                "Projector",
                "Projector issues",
                true,
                true,
                null,
                null);
    }

    // ── GET /incident-categories ──────────────────────────────────────────────

    @Test
    void listCategories_returns200WithList() throws Exception {
        when(categoryService.listCategories(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubCategory()), PageRequest.of(0, 20), 1));

        mvc.perform(get("/incident-categories")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident categories retrieved successfully"))
                .andExpect(jsonPath("$.data.items[0].id").value("cat-1"))
                .andExpect(jsonPath("$.data.items[0].name").value("Facility"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    // ── GET /incident-categories/all ─────────────────────────────────────────

    @Test
    void listAllCategories_adminWithNoFilter_returns200WithAllCategories() throws Exception {
        IncidentCategoryResponse inactive = new IncidentCategoryResponse("cat-2", "Old", "desc", null, false, null);
        when(categoryService.listAllCategories(any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(stubCategory(), inactive), PageRequest.of(0, 20), 2));

        mvc.perform(get("/incident-categories/all")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident categories retrieved successfully"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.items[0].status").value(true))
                .andExpect(jsonPath("$.data.items[1].status").value(false));
    }

    @Test
    void listAllCategories_withStatusFilter_passesStatusToService() throws Exception {
        when(categoryService.listAllCategories(eq(false), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mvc.perform(get("/incident-categories/all?status=false")
                        .with(authentication(new UsernamePasswordAuthenticationToken(
                                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        verify(categoryService).listAllCategories(eq(false), any(), any());
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
                                new IncidentCategoryRequest("Facility", "Facility incidents", "dept-1")))
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
                                new IncidentCategoryRequest("Facility", "Duplicate", "dept-1")))
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
                                new UpdateIncidentCategoryRequest("Updated", "Updated description", "dept-1")))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident category updated successfully"));
    }

    @Test
    void updateCategory_nameOnly_returns200() throws Exception {
        when(categoryService.updateCategory(eq("cat-1"), any())).thenReturn(stubCategory());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.update"));

        mvc.perform(patch("/incident-categories/cat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated Category Name\"}")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident category updated successfully"));
    }

    @Test
    void updateCategory_duplicateName_returns409() throws Exception {
        when(categoryService.updateCategory(eq("cat-1"), any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.update"));

        mvc.perform(patch("/incident-categories/cat-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIncidentCategoryRequest("Facility", "Updated description", "dept-1")))
                        .with(authentication(auth)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A record with this value already exists. Please use a different value."));
    }

    // ── PATCH /incident-categories/{id}/status ─────────────────────────────

    @Test
    void updateCategoryStatus_deactivate_returns200() throws Exception {
        when(categoryService.updateCategoryStatus("cat-1", false)).thenReturn(stubCategory());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.delete"));

        mvc.perform(patch("/incident-categories/cat-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIncidentCategoryStatusRequest(false)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident category status updated successfully"));
    }

    @Test
    void updateCategoryStatus_activate_returns200() throws Exception {
        when(categoryService.updateCategoryStatus("cat-1", true)).thenReturn(stubCategory());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "incident-category.delete"));

        mvc.perform(patch("/incident-categories/cat-1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new UpdateIncidentCategoryStatusRequest(true)))
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident category status updated successfully"));
    }

    // ── GET /incident-categories/{id}/topics ─────────────────────────────────

    @Test
    void listTopics_returns200WithAllTopics() throws Exception {
        IncidentTopicResponse inactiveTopic = new IncidentTopicResponse("type-2", "Old Topic", "Deprecated", true, false, null, null);
        when(categoryService.listTopicsByCategory("cat-1", null))
                .thenReturn(List.of(stubTopic(), inactiveTopic));

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN"));

        mvc.perform(get("/incident-categories/cat-1/topics")
                        .with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Incident topics retrieved successfully"))
                .andExpect(jsonPath("$.data[0].id").value("type-1"))
                .andExpect(jsonPath("$.data[0].status").value(true))
                .andExpect(jsonPath("$.data[1].status").value(false));
    }

    @Test
    void listTopics_withStatusFilter_passesStatusToService() throws Exception {
        when(categoryService.listTopicsByCategory("cat-1", false))
                .thenReturn(List.of());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN"));

        mvc.perform(get("/incident-categories/cat-1/topics")
                        .param("status", "false")
                        .with(authentication(auth)))
                .andExpect(status().isOk());

        verify(categoryService).listTopicsByCategory("cat-1", false);
    }

    // ── POST /incident-categories/{id}/topics ─────────────────────────────────

    @Test
    void createTopic_validRequest_returns201() throws Exception {
        when(categoryService.createTopic(any(), any(), any(CreateTopicRequest.class)))
                .thenReturn(stubTopic());

        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN", () -> "incident-type.create"));

        mvc.perform(post("/incident-categories/cat-1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTopicRequest("Projector", "Projector issues", "group-1", true)))
                        .with(authentication(auth)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Incident topic created successfully"))
                .andExpect(jsonPath("$.data.id").value("type-1"));

        verify(categoryService).createTopic(eq("cat-1"), any(), any(CreateTopicRequest.class));
    }

    @Test
    void createTopic_missingAgentGroupId_returns400() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(
                adminPrincipal(), null, List.of(() -> "ROLE_ADMIN", () -> "incident-type.create"));

        mvc.perform(post("/incident-categories/cat-1/topics")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                        .with(authentication(auth)))
                .andExpect(status().isBadRequest());
    }

}
