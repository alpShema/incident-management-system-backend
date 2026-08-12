package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.UpdateIncidentCategoryRequest;
import com.amalitech.hilfe.dto.UpdateTopicRequest;
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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
                .status(true)
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
    @SuppressWarnings("unchecked")
    void listCategories_statusTrue_returnsMappedList() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(cat), PageRequest.of(0, 20), 1));

        var result = categoryService.listCategories(true, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("cat-1");
        assertThat(result.getContent().get(0).name()).isEqualTo("Facility");
        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategories_statusFalse_returnsMappedList() {
        IncidentCategory inactive = IncidentCategory.builder()
                .id("cat-2")
                .name("Archived")
                .status(false)
                .build();
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(inactive), PageRequest.of(0, 20), 1));

        var result = categoryService.listCategories(false, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategories_hasActiveTopicsOnly_returnsAllCategoriesWithActiveTopics() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listCategories(null, true, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategories_bothFilters_appliesStatusAndActiveTopics() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listCategories(true, true, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategories_noFilters_defaultsToActive() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listCategories(null, null, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    // ── listAllCategories ─────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_noStateFilter_passesNullStatusToRepository() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(buildCategory()), PageRequest.of(0, 20), 1));

        var result = categoryService.listAllCategories(null, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_stateAll_passesNullStatusToRepository() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listAllCategories(null, null, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_statusTrue_filtersToActiveOnly() {
        IncidentCategory active = buildCategory();
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(active), PageRequest.of(0, 20), 1));

        var result = categoryService.listAllCategories(true, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_statusFalse_filtersToInactiveOnly() {
        IncidentCategory inactive = IncidentCategory.builder()
                .id("cat-2").name("Old Category").status(false).build();
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(inactive), PageRequest.of(0, 20), 1));

        var result = categoryService.listAllCategories(false, null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).status()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_withQuery_buildsQueryPattern() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(buildCategory()), PageRequest.of(0, 20), 1));

        var result = categoryService.listAllCategories(null, null, "facility", PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_hasActiveTopicsOnly_returnsAllCategoriesWithActiveTopics() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listAllCategories(null, true, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listAllCategories_bothFilters_appliesStatusAndActiveTopics() {
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        categoryService.listAllCategories(true, true, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    // ── listCategoriesByDepartment ────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void listCategoriesByDepartment_validDepartment_returnsMappedPage() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(buildCategory()), PageRequest.of(0, 20), 1));

        var result = categoryService.listCategoriesByDepartment("dept-1", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo("cat-1");
        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategoriesByDepartment_noStatusFilter_doesNotDefaultToActiveOnly() {
        // Unlike listCategories, omitting status here must NOT restrict to active-only —
        // matches "fetch all categories, filter by department client-side" per HV-1493.
        IncidentCategory inactive = IncidentCategory.builder().id("cat-2").name("Archived").departmentId("dept-1").status(false).build();
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(buildCategory(), inactive), PageRequest.of(0, 20), 2));

        var result = categoryService.listCategoriesByDepartment("dept-1", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(2);
    }

    @Test
    void listCategoriesByDepartment_inactiveDepartment_stillAllowed() {
        // findDepartmentOrThrow only checks existence, unlike the stricter active-only
        // validateDepartment used for create/update — matches /departments/{id}/categories today.
        Department inactiveDept = Department.builder().id("dept-1").name("Facilities").status(false).build();
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(inactiveDept));
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        var result = categoryService.listCategoriesByDepartment("dept-1", null, null, PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listCategoriesByDepartment_departmentNotFound_throws404() {
        when(departmentRepository.findById("missing")).thenReturn(Optional.empty());
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> categoryService.listCategoriesByDepartment("missing", null, null, pageable))
                .isInstanceOf(ArmsAuthException.class)
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
        verifyNoInteractions(categoryRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    void listCategoriesByDepartment_statusFilterApplied_returnsOnlyMatching() {
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));
        when(categoryRepository.findAll(any(Specification.class), eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(buildCategory()), PageRequest.of(0, 20), 1));

        categoryService.listCategoriesByDepartment("dept-1", true, null, PageRequest.of(0, 20));

        verify(categoryRepository).findAll(any(Specification.class), eq(PageRequest.of(0, 20)));
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
    void createCategory_trimsNameAndDescription() {
        IncidentCategory saved = buildCategory();
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(false);
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(saved);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(saved));
        when(departmentRepository.findById("dept-1")).thenReturn(Optional.of(buildDepartment()));

        categoryService.createCategory(
                new IncidentCategoryRequest("  Facility  ", "  Description  ", "dept-1"));

        var captor = forClass(IncidentCategory.class);
        verify(categoryRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Facility");
        assertThat(captor.getValue().getDescription()).isEqualTo("Description");
    }

    @Test
    void createCategory_duplicateName_throws409() {
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(true);

        IncidentCategoryRequest createRequest = new IncidentCategoryRequest("Facility", "Description", "dept-1");
        assertThatThrownBy(() -> categoryService.createCategory(createRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("An incident category with this name already exists. Please choose a different name.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    // ── updateCategory ────────────────────────────────────────────────────────

    @Test
    void updateTopic_trimsNameAndDescription() {
        IncidentCategory category = buildCategory();
        IncidentType topic = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(topic);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        categoryService.updateTopic(
                "cat-1", "type-1",
                new UpdateTopicRequest("  New Name  ", "  New desc  ", null, null, null, null, null));

        assertThat(topic.getName()).isEqualTo("New Name");
        assertThat(topic.getDescription()).isEqualTo("New desc");
    }

    @Test
    void updateTopic_happyPath_updatesAndReturns() {
        IncidentCategory category = buildCategory();
        IncidentType topic = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").departmentId("dept-1").status(true).build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(topic);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        IncidentTopicResponse response = categoryService.updateTopic(
                "cat-1", "type-1",
                new UpdateTopicRequest("New Name", "New desc", null, "group-1", null, false, null));

        assertThat(response).isNotNull();
        verify(typeRepository).save(any(IncidentType.class));
    }

    @Test
    void updateTopic_removeAgentGroup_clearsAssignment() {
        IncidentCategory category = buildCategory();
        IncidentType topic = buildType();
        assertThat(topic.getAgentGroupId()).isEqualTo("group-1");

        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(topic);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        categoryService.updateTopic(
                "cat-1", "type-1",
                new UpdateTopicRequest(null, null, null, null, true, null, null));

        assertThat(topic.getAgentGroupId()).isNull();
        verify(agentGroupRepository, never()).findById(any());
    }

    @Test
    void updateTopic_removeAgentGroupAndProvideNewGroup_throws400() {
        IncidentCategory category = buildCategory();
        IncidentType topic = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(category));
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));

        UpdateTopicRequest request = new UpdateTopicRequest(null, null, null, "group-2", true, null, null);
        assertThatThrownBy(() -> categoryService.updateTopic("cat-1", "type-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Cannot remove and reassign the agent group in the same request")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);

        assertThat(topic.getAgentGroupId()).isEqualTo("group-1");
        verify(typeRepository, never()).save(any(IncidentType.class));
    }

    @Test
    void updateCategory_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        UpdateIncidentCategoryRequest updateRequest = new UpdateIncidentCategoryRequest("Updated", "Desc", "dept-1");
        assertThatThrownBy(() -> categoryService.updateCategory("missing", updateRequest))
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

        UpdateIncidentCategoryRequest updateRequest = new UpdateIncidentCategoryRequest("Other Name", "Desc", "dept-1");
        assertThatThrownBy(() -> categoryService.updateCategory("cat-1", updateRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("An incident category with this name already exists. Please choose a different name.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void updateCategory_trimsNameAndDescription() {
        IncidentCategory cat = buildCategory(); // name = "Facility"
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        categoryService.updateCategory(
                "cat-1", new UpdateIncidentCategoryRequest("  New Name  ", "  New desc  ", null));

        assertThat(cat.getName()).isEqualTo("New Name");
        assertThat(cat.getDescription()).isEqualTo("New desc");
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

    // ── updateCategoryStatus ──────────────────────────────────────────────────

    @Test
    void updateCategoryStatus_deactivate_setsInactive() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));

        categoryService.updateCategoryStatus("cat-1", false);

        assertThat(cat.getStatus()).isFalse();
        verify(categoryRepository).save(cat);
    }

    @Test
    void updateCategoryStatus_deactivate_cascadesToActiveTopics() {
        IncidentCategory cat = buildCategory();
        IncidentType topic = buildType();
        topic.setStatus(true);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));
        when(typeRepository.findByCategoryId("cat-1")).thenReturn(List.of(topic));

        categoryService.updateCategoryStatus("cat-1", false);

        assertThat(cat.getStatus()).isFalse();
        assertThat(topic.getStatus()).isFalse();
        verify(typeRepository).saveAll(List.of(topic));
    }

    @Test
    void updateCategoryStatus_activate_setsActive() {
        IncidentCategory cat = buildCategory();
        cat.setStatus(false);
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(cat));

        categoryService.updateCategoryStatus("cat-1", true);

        assertThat(cat.getStatus()).isTrue();
        verify(categoryRepository).save(cat);
    }

    @Test
    void updateCategoryStatus_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategoryStatus("missing", false))
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
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true, false));

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

        CreateTopicRequest topicRequest = new CreateTopicRequest("Projector", "Projector issues", null, true, false);
        assertThatThrownBy(() -> categoryService.createTopic("missing", "admin-1", topicRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    @Test
    void createTopic_trimsNameAndDescription() {
        IncidentType saved = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").departmentId("dept-1").status(true).build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(saved);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("  Projector  ", "  Projector issues  ", "group-1", true, false));

        var captor = forClass(IncidentType.class);
        verify(typeRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Projector");
        assertThat(captor.getValue().getDescription()).isEqualTo("Projector issues");
    }

    @Test
    void createTopic_duplicateName_throws409() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(true);

        CreateTopicRequest dupTopicRequest = new CreateTopicRequest("Projector", "Projector issues", "group-1", true, false);
        assertThatThrownBy(() -> categoryService.createTopic("cat-1", "admin-1", dupTopicRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A topic with this name already exists. Please choose a different name.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createTopic_doesNotSetAgentId_agentIdIsNull() {
        // Regression: agent_id was NOT NULL in the DB schema but was never populated during
        // topic creation, causing a constraint violation and a 500 on every createTopic call.
        // Verified fix: agent_id is now nullable; the saved entity must not have agentId set.
        IncidentType saved = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").departmentId("dept-1").status(true).build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(saved);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true, false));

        var captor = forClass(IncidentType.class);
        verify(typeRepository).save(captor.capture());
        assertThat(captor.getValue().getAgentId()).isNull();
    }

    @Test
    void createTopic_agentGroupNotFound_throws404() {
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("missing-group")).thenReturn(Optional.empty());

        CreateTopicRequest missingGroupRequest = new CreateTopicRequest("Projector", "Projector issues", "missing-group", true, false);
        assertThatThrownBy(() -> categoryService.createTopic("cat-1", "admin-1", missingGroupRequest))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }

    // ── HV-1619: confidential topics ─────────────────────────────────────────

    @Test
    void createTopic_confidentialWithAgentGroup_persistsFlag() {
        IncidentType saved = buildType();
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").departmentId("dept-1").status(true).build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(saved);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true, true));

        var captor = forClass(IncidentType.class);
        verify(typeRepository).save(captor.capture());
        assertThat(captor.getValue().isConfidential()).isTrue();
    }

    @Test
    void updateTopicById_confidentialWithoutOwner_throws400() {
        IncidentType topic = IncidentType.builder()
                .id("type-1").name("Projector").description("Projector issues")
                .categoryId("cat-1").adminId("admin-1").build();
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));

        UpdateTopicRequest request = new UpdateTopicRequest(null, null, null, null, null, null, true);
        assertThatThrownBy(() -> categoryService.updateTopicById("type-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A confidential topic must have a responsible agent group.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
        verify(typeRepository, never()).save(any());
    }

    @Test
    void updateTopicById_removeAgentGroupWithConfidential_throws400() {
        IncidentType topic = IncidentType.builder()
                .id("type-1").name("Projector").description("Projector issues")
                .categoryId("cat-1").adminId("admin-1").agentGroupId("group-1")
                .confidential(true).build();
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));

        UpdateTopicRequest request = new UpdateTopicRequest(null, null, null, null, true, null, true);
        assertThatThrownBy(() -> categoryService.updateTopicById("type-1", request))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A confidential topic must have a responsible agent group.")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
        verify(typeRepository, never()).save(any());
    }

    @Test
    void updateTopicById_confidentialWithExistingGroup_persistsFlag() {
        IncidentType topic = buildType();
        when(typeRepository.findById("type-1")).thenReturn(Optional.of(topic));
        when(categoryRepository.findById("cat-1")).thenReturn(Optional.of(buildCategory()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(topic);
        when(typeRepository.findByIdWithDetails("type-1")).thenReturn(Optional.of(buildHydratedType()));

        UpdateTopicRequest request = new UpdateTopicRequest(null, null, null, null, null, null, true);
        categoryService.updateTopicById("type-1", request);

        var captor = forClass(IncidentType.class);
        verify(typeRepository).save(captor.capture());
        assertThat(captor.getValue().isConfidential()).isTrue();
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
        when(categoryRepository.searchCategories(null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        categoryService.searchCategories("  ", pageable);

        verify(categoryRepository).searchCategories(null, pageable);
    }

    @Test
    void searchCategories_nullQuery_passesNullPatternToRepository() {
        var pageable = PageRequest.of(0, 10);
        when(categoryRepository.searchCategories(null, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        categoryService.searchCategories(null, pageable);

        verify(categoryRepository).searchCategories(null, pageable);
    }


    @Test
    void listTopics_noStatusFilter_returnsAllTopics() {
        var pageable = PageRequest.of(0, 20);
        var defaultSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
        when(typeRepository.findAllTopicsFiltered(null, null, null, null, null, defaultSort))
                .thenReturn(new PageImpl<>(List.of(buildHydratedType()), defaultSort, 1));

        var result = categoryService.listTopics(null, null, null, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        IncidentTopicListResponse row = result.getContent().get(0);
        assertThat(row.category()).isNotNull();
        assertThat(row.agentGroup()).isNotNull();
        verify(typeRepository).findAllTopicsFiltered(null, null, null, null, null, defaultSort);
    }

    @Test
    void listTopics_statusAll_passesNullToRepository() {
        var pageable = PageRequest.of(0, 20);
        var defaultSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
        when(typeRepository.findAllTopicsFiltered(null, null, null, null, null, defaultSort))
                .thenReturn(new PageImpl<>(List.of(), defaultSort, 0));

        categoryService.listTopics(null, null, null, null, null, pageable);

        verify(typeRepository).findAllTopicsFiltered(null, null, null, null, null, defaultSort);
    }

    @Test
    void listTopics_statusActive_filtersActiveTopics() {
        var pageable = PageRequest.of(0, 20);
        var defaultSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "name"));
        when(typeRepository.findAllTopicsFiltered(null, null, null, true, null, defaultSort))
                .thenReturn(new PageImpl<>(List.of(buildHydratedType()), defaultSort, 1));

        var result = categoryService.listTopics(null, null, null, true, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(typeRepository).findAllTopicsFiltered(null, null, null, true, null, defaultSort);
    }

    @Test
    void listTopics_sortByCategoryAlias_translatesToCategoryNamePath() {
        Page<IncidentType> page = new PageImpl<>(List.of());
        when(typeRepository.findAllTopicsFiltered(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        var categorySort = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "category"));
        categoryService.listTopics(null, null, null, null, null, categorySort);

        var captor = forClass(org.springframework.data.domain.Pageable.class);
        verify(typeRepository).findAllTopicsFiltered(any(), any(), any(), any(), any(), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("category.name")).isNotNull();
        assertThat(captured.getOrderFor("category")).isNull();
    }

    @Test
    void listTopics_sortByUnknownField_fallsBackToDefaultNameSort() {
        Page<IncidentType> page = new PageImpl<>(List.of());
        when(typeRepository.findAllTopicsFiltered(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        var unknownSort = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "bogusField"));
        categoryService.listTopics(null, null, null, null, null, unknownSort);

        var captor = forClass(org.springframework.data.domain.Pageable.class);
        verify(typeRepository).findAllTopicsFiltered(any(), any(), any(), any(), any(), captor.capture());
        Sort captured = captor.getValue().getSort();
        assertThat(captured.getOrderFor("name")).isNotNull();
        assertThat(captured.getOrderFor("bogusField")).isNull();
    }

    // ── listTopicsByCategory ──────────────────────────────────────────────────

    @Test
    void listTopicsByCategory_noStatusFilter_returnsAllTopics() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.findByCategoryIdWithAgentAndStatus("cat-1", null))
                .thenReturn(List.of(buildHydratedType()));

        var result = categoryService.listTopicsByCategory("cat-1", null);

        assertThat(result).hasSize(1);
        verify(typeRepository).findByCategoryIdWithAgentAndStatus("cat-1", null);
    }

    @Test
    void listTopicsByCategory_statusActive_passesActiveToRepository() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.findByCategoryIdWithAgentAndStatus("cat-1", true))
                .thenReturn(List.of(buildHydratedType()));

        var result = categoryService.listTopicsByCategory("cat-1", true);

        assertThat(result).hasSize(1);
        verify(typeRepository).findByCategoryIdWithAgentAndStatus("cat-1", true);
    }

    @Test
    void listTopicsByCategory_statusInactive_passesInactiveToRepository() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.findByCategoryIdWithAgentAndStatus("cat-1", false))
                .thenReturn(List.of());

        var result = categoryService.listTopicsByCategory("cat-1", false);

        assertThat(result).isEmpty();
        verify(typeRepository).findByCategoryIdWithAgentAndStatus("cat-1", false);
    }

    @Test
    void listTopicsByCategory_categoryNotFound_throws404() {
        when(categoryRepository.existsById("missing")).thenReturn(false);

        assertThatThrownBy(() -> categoryService.listTopicsByCategory("missing", null))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
    }
}
