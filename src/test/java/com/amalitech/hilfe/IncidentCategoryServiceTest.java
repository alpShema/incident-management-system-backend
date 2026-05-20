package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import com.amalitech.hilfe.repositories.AgentRepository;
import com.amalitech.hilfe.repositories.IncidentCategoryRepository;
import com.amalitech.hilfe.repositories.IncidentRepository;
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import com.amalitech.hilfe.services.IncidentCategoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentCategoryServiceTest {

    @Mock IncidentCategoryRepository categoryRepository;
    @Mock IncidentTypeRepository typeRepository;
    @Mock AgentRepository agentRepository;
    @Mock IncidentRepository incidentRepository;
    @InjectMocks IncidentCategoryService categoryService;

    private IncidentCategory buildCategory() {
        return IncidentCategory.builder()
                .id("cat-1")
                .name("Facility")
                .description("Facility related incidents")
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
                .build();
    }

    // ── listCategories ────────────────────────────────────────────────────────

    @Test
    void listCategories_returnsMappedList() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findByStatus("active")).thenReturn(List.of(cat));

        List<IncidentCategoryResponse> result = categoryService.listCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("cat-1");
        assertThat(result.get(0).name()).isEqualTo("Facility");
    }

    // ── createCategory ────────────────────────────────────────────────────────

    @Test
    void createCategory_happyPath_savesAndReturns() {
        IncidentCategory saved = buildCategory();
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(false);
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(saved);

        IncidentCategoryResponse response = categoryService.createCategory(
                new IncidentCategoryRequest("Facility", "Description"));

        assertThat(response.id()).isEqualTo("cat-1");
        assertThat(response.name()).isEqualTo("Facility");
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void createCategory_duplicateName_throws409() {
        when(categoryRepository.existsByNameIgnoreCase("Facility")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(
                new IncidentCategoryRequest("Facility", "Description")))
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
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);

        IncidentCategoryResponse response = categoryService.updateCategory(
                "cat-1", new IncidentCategoryRequest("Updated", "New description"));

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void updateCategory_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                "missing", new IncidentCategoryRequest("Updated", "Desc")))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Incident category not found")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(404);
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
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentRepository.findByUserId("admin-1")).thenReturn(Optional.of(
                Agent.builder().id("agent-1").userId("admin-1").agentGroupId("dept-1").build()));
        when(typeRepository.save(any(IncidentType.class))).thenReturn(saved);

        IncidentTopicResponse response = categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", null, true));

        assertThat(response.id()).isEqualTo("type-1");
        assertThat(response.name()).isEqualTo("Projector");
        var topicCaptor = forClass(IncidentType.class);
        verify(typeRepository).save(topicCaptor.capture());
        assertThat(topicCaptor.getValue().getAdminId()).isEqualTo("admin-1");
        assertThat(topicCaptor.getValue().getAgentId()).isEqualTo("agent-1");
    }

    @Test
    void createTopic_categoryNotFound_throws404() {
        when(categoryRepository.existsById("missing")).thenReturn(false);

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
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A topic with this name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createTopic_authenticatedUserWithoutAgent_throws403() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentRepository.findByUserId("admin-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", null, true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Authenticated user is not linked to an agent record")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(403);
    }
}
