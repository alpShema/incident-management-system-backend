package com.amalitech.hilfe.repositories.specifications;

import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.domain.Specification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the Active Category Filter and Active Topics Filter each apply their own
 * condition in isolation, independent of one another, per the category-filtering enhancement.
 */
class IncidentCategorySpecificationsTest {

    @Test
    @SuppressWarnings("unchecked")
    void isActiveCategory_checksOnlyCategoryStatus() {
        Root<IncidentCategory> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Boolean> statusPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);

        when(root.<Boolean>get("status")).thenReturn(statusPath);
        when(cb.isTrue(statusPath)).thenReturn(expected);

        Predicate result = IncidentCategorySpecifications.isActiveCategory().toPredicate(root, query, cb);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasActiveTopics_checksOnlyForAnActiveLinkedTopic_regardlessOfCategoryStatus() {
        Root<IncidentCategory> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Subquery<Integer> subquery = mock(Subquery.class);
        Root<IncidentType> topicRoot = mock(Root.class);
        Path<Object> categoryIdPath = mock(Path.class);
        Path<Object> idPath = mock(Path.class);
        Path<Boolean> topicStatusPath = mock(Path.class);
        Predicate categoryMatch = mock(Predicate.class);
        Predicate statusMatch = mock(Predicate.class);
        Predicate exists = mock(Predicate.class);

        when(query.subquery(Integer.class)).thenReturn(subquery);
        when(subquery.from(IncidentType.class)).thenReturn(topicRoot);
        when(subquery.select(any())).thenReturn(subquery);
        when(root.<Object>get("id")).thenReturn(idPath);
        when(topicRoot.<Object>get("categoryId")).thenReturn(categoryIdPath);
        when(topicRoot.<Boolean>get("status")).thenReturn(topicStatusPath);
        when(cb.equal(categoryIdPath, idPath)).thenReturn(categoryMatch);
        when(cb.isTrue(topicStatusPath)).thenReturn(statusMatch);
        when(subquery.where(categoryMatch, statusMatch)).thenReturn(subquery);
        when(cb.exists(subquery)).thenReturn(exists);

        Predicate result = IncidentCategorySpecifications.hasActiveTopics().toPredicate(root, query, cb);

        assertThat(result).isSameAs(exists);
        verify(subquery).where(categoryMatch, statusMatch);
    }

    @Test
    void isActiveCategory_isInvocableWithoutHasActiveTopics() {
        Specification<IncidentCategory> spec = IncidentCategorySpecifications.isActiveCategory();
        assertThat(spec).isNotNull();
    }

    @Test
    void hasActiveTopics_isInvocableWithoutIsActiveCategory() {
        Specification<IncidentCategory> spec = IncidentCategorySpecifications.hasActiveTopics();
        assertThat(spec).isNotNull();
    }

    @Test
    void bothFilters_composeViaAnd() {
        Specification<IncidentCategory> combined = IncidentCategorySpecifications.isActiveCategory()
                .and(IncidentCategorySpecifications.hasActiveTopics());

        assertThat(combined).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void hasDepartmentId_checksOnlyDepartmentIdEquality() {
        Root<IncidentCategory> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Path<Object> departmentIdPath = mock(Path.class);
        Predicate expected = mock(Predicate.class);

        when(root.<Object>get("departmentId")).thenReturn(departmentIdPath);
        when(cb.equal(departmentIdPath, "dept-1")).thenReturn(expected);

        Predicate result = IncidentCategorySpecifications.hasDepartmentId("dept-1").toPredicate(root, query, cb);

        assertThat(result).isSameAs(expected);
    }

    @Test
    @SuppressWarnings("unchecked")
    void matchesQuery_returnsAlwaysTruePredicate_andSkipsJoins_whenPatternIsNull() {
        Root<IncidentCategory> root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate always = mock(Predicate.class);
        when(cb.conjunction()).thenReturn(always);

        Predicate result = IncidentCategorySpecifications.matchesQuery(null).toPredicate(root, query, cb);

        assertThat(result).isSameAs(always);
        org.mockito.Mockito.verifyNoInteractions(root);
    }
}
