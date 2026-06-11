package com.amalitech.hilfe;

import com.amalitech.hilfe.controllers.PermissionController;
import com.amalitech.hilfe.dto.PermissionCatalogResponse;
import com.amalitech.hilfe.exceptions.GlobalExceptionHandler;
import com.amalitech.hilfe.security.Http401AuthenticationEntryPoint;
import com.amalitech.hilfe.security.JwtAuthenticationFilter;
import com.amalitech.hilfe.security.SecurityConfig;
import com.amalitech.hilfe.services.RoleService;
import com.amalitech.hilfe.services.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PermissionController.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class, JwtAuthenticationFilter.class, Http401AuthenticationEntryPoint.class})
@TestPropertySource(properties = "cors.allowed-origins=http://localhost")
class PermissionControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean RoleService roleService;
    @MockitoBean TokenService tokenService;

    private UsernamePasswordAuthenticationToken withPermission() {
        return new UsernamePasswordAuthenticationToken(
                "admin", null, List.of(() -> "rbac.permission.read"));
    }

    private UsernamePasswordAuthenticationToken withoutPermission() {
        return new UsernamePasswordAuthenticationToken(
                "client", null, List.of(() -> "ROLE_CLIENT"));
    }

    @Test
    void catalog_withPermission_returns200() throws Exception {
        var catalog = new PermissionCatalogResponse(List.of(), List.of(), List.of(), List.of(), List.of());
        when(roleService.permissionCatalog()).thenReturn(catalog);

        mvc.perform(get("/permissions/catalog")
                        .with(authentication(withPermission())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Permission catalog retrieved successfully"));
    }

    @Test
    void catalog_withoutPermission_returns403() throws Exception {
        mvc.perform(get("/permissions/catalog")
                        .with(authentication(withoutPermission())))
                .andExpect(status().isForbidden());
    }
}
