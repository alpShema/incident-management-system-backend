package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.UpdateIncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicListResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.models.User;
import com.amalitech.hilfe.repositories.AgentGroupRepository;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.DepartmentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import com.amalitech.hilfe.services.IncidentCategoryService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentCategoryServiceTest {

    @Mock IncidentCategoryRepository categoryRepository;
    @Mock IncidentTypeRepository typeRepository;
    @Mock AgentGroupRepository agentGroupRepository;
    @Mock DepartmentRepository departmentRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock EntityManager entityManager;
    @InjectMocks IncidentCategoryService categoryService;

    private IncidentCategory buildCategory() {
        return IncidentCategory.builder()
                .id("cat-1")
                .name("Facility")
                .description("Facility related incidents")
                .departmentId("dept-1")
                .status("active")
                .build();
    }

    private IncidentType buildType() {
        return IncidentType.builder()
                .id("type-1")
                .name("Projector")
                .description("Projector issues")
                .categoryId("cat-1")
                .adminId("admin-1")
                .agentId("agent-1")
                .agentGroupId("group-1")
                .build();
    }

    private IncidentType buildHydratedType() {
        IncidentType type = buildType();
        type.setCategory(buildCategory());
        Agent agent = Agent.builder()
                .id("agent-1")
                .userId("agent-user-1")
                .agentGroupId("dept-1")
                .build();
        agent.setUser(User.builder()
                .id("agent-user-1")
                .email("agent@test.com")
                .fullName("Agent One")
                .build());
        AgentGroup group = AgentGroup.builder()
                .id("group-1")
                .name("IT Support")
                .departmentId("dept-1")
                .build();
        type.setAgentGroup(group);
        return type;
    }

    private Department buildDepartment() {
        return Department.builder()
                .id("dept-1")
                .name("Facilities")
                .status(true)
                .build();
    }

    // ── listCategories ────────────────────────────────────────────────────────

    @Test
    void listCategories_returnsMappedList() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findByStatusWithDepartmentAndQueryPaged("active", null, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(cat), PageRequest.of(0, 20), 1));

        var result = categoryService.listCategories("active", null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("cat-1");
        assertThat(result.getContent().get(0).name()).isEqualTo("Facility");
    }

    // ── createCategory ────────────────────────────────────────────────────────

    @Test
    void createCategory_happyPath_savesAndReturns() {
        IncidentCategory saved = buildCategory();
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(false);
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(saved);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(saved));

        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));

        IncidentCategoryResponse response = categoryService.createCategory(
                new IncidentCategoryRequest("Facility", "Description", "dept-1"));

        assertThat(response.id()).isEqualTo("cat-1");
        assertThat(response.name()).isEqualTo("Facility");
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void createCategory_duplicateName_throws409() {
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                new IncidentCategoryRequest("Facility", "Description", "dept-1")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category with this name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    // ── updateCategory ────────────────────────────────────────────────────────

    @Test
    void updateCategory_found_updatesAndReturns() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        IncidentCategoryResponse response = categoryService.updateCategory(
                "cat-1", new UpdateIncidentCategoryRequest("Updated", "New description", "dept-1"));

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void updateCategory_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                "missing", new UpdateIncidentCategoryRequest("Updated", "Desc", "dept-1")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void updateCategory_duplicateName_throws409() {
        IncidentCategory cat = buildCategory(); // name = "Facility"
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.existsByNameIgnoreCase("Other Name")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.updateCategory(
                "cat-1", new UpdateIncidentCategoryRequest("Other Name", "Desc", "dept-1")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category with this name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateCategory_sameNameIgnoreCase_doesNotThrow409() {
        IncidentCategory cat = buildCategory(); // name = "Facility"
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        // Updating to the same name (case-insensitive) must not trigger the duplicate check
        IncidentCategoryResponse response = categoryService.updateCategory(
                "cat-1", new UpdateIncidentCategoryRequest("FACILITY", "Updated desc", "dept-1"));

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void updateCategory_nameOnly_doesNotRequireDepartmentId() {
        IncidentCategory cat = buildCategory(); // departmentId = "dept-1"
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        // No departmentId supplied — should not throw and should preserve existing departmentId
        categoryService.updateCategory("cat-1", new UpdateIncidentCategoryRequest("New Name", null, null));

        assertThat(cat.getDepartmentId()).isEqualTo("dept-1"); // unchanged
        assertThat(cat.getName()).isEqualTo("New Name");
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void updateCategory_emptyRequest_changesNothing() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        // All nulls — nothing changes
        categoryService.updateCategory("cat-1", new UpdateIncidentCategoryRequest(null, null, null));

        assertThat(cat.getName()).isEqualTo("Facility");
        assertThat(cat.getDepartmentId()).isEqualTo("dept-1");
    }

    // ── deleteCategory ────────────────────────────────────────────────────────

    @Test
    void deleteCategory_exists_deactivatesCategory() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));

        categoryService.deleteCategory("cat-1");

        assertThat(cat.getStatus()).isEqualTo("inactive");
        verify(categoryRepository).save(cat);
    }

    @Test
    void deleteCategory_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.deleteCategory("missing"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── listTopicsByCategory ──────────────────────────────────────────────────

    @Test
    void listTopicsByCategory_categoryExists_returnsTopicList() {
        IncidentType type = buildType();
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.findByCategoryIdWithAgent("cat-1")).thenReturn(List.of(type));

        List<IncidentTopicResponse> result = categoryService.listTopicsByCategory("cat-1");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("type-1");
        assertThat(result.get(0).name()).isEqualTo("Projector");
    }

    @Test
    void listTopicsByCategory_categoryNotFound_throws404() {
        when(categoryRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> categoryService.listTopicsByCategory("missing"))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── createTopic ───────────────────────────────────────────────────────────

    @Test
    void createTopic_happyPath_savesAndReturns() {
        IncidentType saved = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").departmentId("dept-1").status(true).build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(saved);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        IncidentTopicResponse response = categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true));

        assertThat(response.id()).isEqualTo("type-1");
        assertThat(response.name()).isEqualTo("Projector");
        assertThat(response.category()).isNotNull();
        assertThat(response.category().id()).isEqualTo("cat-1");
        assertThat(response.category().name()).isEqualTo("Facility");
        assertThat(response.visibleToGroup()).isTrue();
        var topicCaptor = forClass(IncidentType.class);
        verify(typeRepository).save(topicCaptor.capture());
        verify(entityManager).flush();
        verify(entityManager).clear();
        assertThat(topicCaptor.getValue().getAdminId()).isEqualTo("admin-1");
        assertThat(topicCaptor.getValue().getAgentGroupId()).isEqualTo("group-1");
    }

    @Test
    void createTopic_categoryNotFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createTopic(
                "missing", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createTopic_duplicateName_throws409() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A topic with this name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createTopic_agentGroupNotFound_throws404() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("missing-group")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "missing-group", true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── searchCategories ──────────────────────────────────────────────────────

    @Test
    void searchCategories_withQuery_passesPatternToRepository() {
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.searchCategories(any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(buildCategory())));

        var result = categoryService.searchCategories("facility", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Facility");
        verify(categoryRepository).searchCategories("%facility%", pageable);
    }

    @Test
    void searchCategories_blankQuery_passesNullPatternToRepository() {
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.searchCategories(eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        categoryService.searchCategories("  ", pageable);

        verify(categoryRepository).searchCategories(null, pageable);
    }

    @Test
    void searchCategories_nullQuery_passesNullPatternToRepository() {
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.searchCategories(eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of()));

        categoryService.searchCategories(null, pageable);

        verify(categoryRepository).searchCategories(null, pageable);
    }


    @Test
    void listTopics_defaultsStatusToActive() {
        var pageable = PageRequest.of(0, 20);
        when(typeRepository.findAllTopicsFiltered(
                eq(null), eq(null), eq(null), eq("active"), eq(null), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(buildHydratedType()), pageable, 1));

        var result = categoryService.listTopics(null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        IncidentTopicListResponse row = result.getContent().get(0);
        assertThat(row.category()).isNotNull();
        assertThat(row.agentGroup()).isNotNull();
        verify(typeRepository).findAllTopicsFiltered(null, null, null, "active", null, pageable);
    }

    @Test
    void listTopics_rejectsInvalidStatus() {
        var pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> categoryService.listTopics(null, null, null, "archived", null, pageable))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Invalid status. Allowed values are active or inactive")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }
}
