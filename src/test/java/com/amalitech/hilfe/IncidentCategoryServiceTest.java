package com.amalitech.hilfe;

import com.amalitech.hilfe.dto.CreateTopicRequest;
import com.amalitech.hilfe.dto.IncidentCategoryRequest;
import com.amalitech.hilfe.dto.IncidentCategoryResponse;
import com.amalitech.hilfe.dto.IncidentTopicResponse;
import com.amalitech.hilfe.exceptions.ArmsAuthException;
import com.amalitech.hilfe.models.Agent;
import com.amalitech.hilfe.models.AgentGroup;
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
                .primaryAgentId("agent-1")
                .build();
        group.setPrimaryAgent(agent);
        type.setAgentGroup(group);
        return type;
    }

    // ── listCategories ────────────────────────────────────────────────────────

    @Test
    void listCategories_returnsMappedList() {
        IncidentCategory cat = buildCategory();
        when(categoryRepository.findByStatusWithDepartment("active")).thenReturn(List.of(cat));

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
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(saved));

        when(departmentRepository.existsById("dept-1")).thenReturn(true);

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
        when(departmentRepository.existsById("dept-1")).thenReturn(true);
        when(categoryRepository.save(any(IncidentCategory.class))).thenReturn(cat);
        when(categoryRepository.findByIdWithDepartment("cat-1")).thenReturn(Optional.of(cat));

        IncidentCategoryResponse response = categoryService.updateCategory(
                "cat-1", new IncidentCategoryRequest("Updated", "New description", "dept-1"));

        assertThat(response).isNotNull();
        verify(categoryRepository).save(any(IncidentCategory.class));
    }

    @Test
    void updateCategory_notFound_throws404() {
        when(categoryRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.updateCategory(
                "missing", new IncidentCategoryRequest("Updated", "Desc", "dept-1")))
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
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").primaryAgentId("agent-1").status(true).build()));
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
        assertThat(response.assignedAgentGroup()).isNotNull();
        assertThat(response.assignedAgentGroup().id()).isEqualTo("group-1");
        assertThat(response.assignedAgentGroup().name()).isEqualTo("IT Support");
        assertThat(response.assignedAgentGroup().primaryAgent()).isNotNull();
        assertThat(response.assignedAgentGroup().primaryAgent().id()).isEqualTo("agent-1");
        assertThat(response.assignedAgentGroup().primaryAgent().name()).isEqualTo("Agent One");
        assertThat(response.assignedAgentGroup().primaryAgent().userId()).isEqualTo("agent-user-1");
        assertThat(response.assignedAgentGroup().primaryAgent().email()).isEqualTo("agent@test.com");
        assertThat(response.visibleToGroup()).isTrue();
        var topicCaptor = forClass(IncidentType.class);
        verify(typeRepository).save(topicCaptor.capture());
        verify(entityManager).flush();
        verify(entityManager).clear();
        assertThat(topicCaptor.getValue().getAdminId()).isEqualTo("admin-1");
        assertThat(topicCaptor.getValue().getAgentId()).isEqualTo("agent-1");
        assertThat(topicCaptor.getValue().getAgentGroupId()).isEqualTo("group-1");
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
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("A topic with this name already exists")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(409);
    }

    @Test
    void createTopic_agentGroupNotFound_throws404() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
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

    @Test
    void createTopic_agentGroupWithoutPrimaryAgent_throws400() {
        when(categoryRepository.existsById("cat-1")).thenReturn(true);
        when(typeRepository.existsByNameIgnoreCase("Projector")).thenReturn(false);
        when(agentGroupRepository.findById("group-1")).thenReturn(Optional.of(
                AgentGroup.builder().id("group-1").name("IT Support").status(true).build()));

        assertThatThrownBy(() -> categoryService.createTopic(
                "cat-1", "admin-1",
                new CreateTopicRequest("Projector", "Projector issues", "group-1", true)))
                .isInstanceOf(ArmsAuthException.class)
                .hasMessage("Agent group must have a primary agent")
                .extracting(e -> ((ArmsAuthException) e).getHttpStatus())
                .isEqualTo(400);
    }
}
