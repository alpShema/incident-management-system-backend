package com.amalitech.hilfe.repositories.specifications;

import com.amalitech.hilfe.models.Department;
import com.amalitech.hilfe.models.IncidentCategory;
import com.amalitech.hilfe.models.IncidentType;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

/**
 * Independently invocable, composable filter units for {@link IncidentCategory} queries.
 * Each filter checks exactly one business rule and can be applied alone or combined with
 * {@link Specification#and(Specification)}; neither filter's behaviour depends on whether
 * the other is applied.
 */
public final class IncidentCategorySpecifications {

    private static final String DEPARTMENT_ATTRIBUTE = "department";

    private IncidentCategorySpecifications() {
    }

    /** Active Category Filter: matches categories whose own status is active. */
    public static Specification<IncidentCategory> isActiveCategory() {
        return (root, query, cb) -> cb.isTrue(root.get("status"));
    }

    /** Active Topics Filter: matches categories with at least one active linked topic, regardless of the category's own status. */
    public static Specification<IncidentCategory> hasActiveTopics() {
        return (root, query, cb) -> {
            Subquery<Integer> subquery = query.subquery(Integer.class);
            Root<IncidentType> topic = subquery.from(IncidentType.class);
            subquery.select(cb.literal(1))
                    .where(
                            cb.equal(topic.get("categoryId"), root.get("id")),
                            cb.isTrue(topic.get("status"))
                    );
            return cb.exists(subquery);
        };
    }

    /** Department Filter: matches categories linked to the given department id. */
    public static Specification<IncidentCategory> hasDepartmentId(String departmentId) {
        return (root, query, cb) -> cb.equal(root.get("departmentId"), departmentId);
    }

    /** Matches categories whose name, description, or department name contain the given (already lower-cased, escaped) pattern. */
    public static Specification<IncidentCategory> matchesQuery(String queryPattern) {
        if (queryPattern == null) {
            return (root, query, cb) -> cb.conjunction();
        }
        return (root, query, cb) -> {
            Join<IncidentCategory, Department> department = joinDepartment(root, query);
            return cb.or(
                    cb.like(cb.lower(root.get("name")), queryPattern, '!'),
                    cb.like(cb.lower(root.get("description")), queryPattern, '!'),
                    cb.like(cb.lower(department.get("name")), queryPattern, '!')
            );
        };
    }

    /** Eagerly loads the associated department for the result rows (skipped for count queries). */
    public static Specification<IncidentCategory> withDepartment() {
        return (root, query, cb) -> {
            joinDepartment(root, query);
            return cb.conjunction();
        };
    }

    @SuppressWarnings("unchecked")
    private static Join<IncidentCategory, Department> joinDepartment(Root<IncidentCategory> root, CriteriaQuery<?> query) {
        for (Join<?, ?> join : root.getJoins()) {
            if (DEPARTMENT_ATTRIBUTE.equals(join.getAttribute().getName())) {
                return (Join<IncidentCategory, Department>) join;
            }
        }
        boolean isCountQuery = Long.class.equals(query.getResultType());
        if (isCountQuery) {
            return root.join(DEPARTMENT_ATTRIBUTE, JoinType.LEFT);
        }
        return (Join<IncidentCategory, Department>) (Object) root.fetch(DEPARTMENT_ATTRIBUTE, JoinType.LEFT);
    }
}
