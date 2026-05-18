package com.amalitech.hilfe.services;

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
import com.amalitech.hilfe.repositories.IncidentTypeRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IncidentCategoryService {
    private final IncidentCategoryRepository categoryRepository;
    private final IncidentTypeRepository typeRepository;
    private final AgentRepository agentRepository;

    public List<IncidentCategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(IncidentCategoryResponse::from)
                .toList();
    }

    @Transactional
    public IncidentCategoryResponse createCategory(IncidentCategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("Incident category with this name already exists", 409);
        }
        IncidentCategory category = IncidentCategory.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .build();
        return IncidentCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public IncidentCategoryResponse updateCategory(String id, IncidentCategoryRequest request) {
        IncidentCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> new ArmsAuthException("Incident category not found", 404));
        category.setName(request.name());
        category.setDescription(request.description());
        return IncidentCategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional
    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        categoryRepository.deleteById(id);
    }

    public List<IncidentTopicResponse> listTopicsByCategory(String categoryId) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        return typeRepository.findByCategoryId(categoryId).stream()
                .map(IncidentTopicResponse::from)
                .toList();
    }

    @Transactional
    public IncidentTopicResponse createTopic(String categoryId, String creatorUserId, CreateTopicRequest request) {
        if (!categoryRepository.existsById(categoryId)) {
            throw new ArmsAuthException("Incident category not found", 404);
        }
        if (typeRepository.existsByNameIgnoreCase(request.name())) {
            throw new ArmsAuthException("A topic with this name already exists", 409);
        }
        Agent creatorAgent = agentRepository.findByUserId(creatorUserId)
                .orElseThrow(() -> new ArmsAuthException("Authenticated user is not linked to an agent record", 403));
        IncidentType topic = IncidentType.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .description(request.description())
                .categoryId(categoryId)
                .adminId(creatorUserId)
                .agentId(creatorAgent.getId())
                .visibleToGroup(request.visibleToGroup())
                .build();
        return IncidentTopicResponse.from(typeRepository.save(topic));
    }
}
