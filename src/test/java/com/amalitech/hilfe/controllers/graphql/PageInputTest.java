package com.amalitech.hilfe.controllers.graphql;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PageInputTest {

    @Test
    void toPageable_withoutSortBy_isUnsorted() {
        var pageable = new PageInput(0, 10, null, null).toPageable();

        assertThat(pageable.getSort().isUnsorted()).isTrue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
    }

    @Test
    void toPageable_withSortByAndAsc_buildsAscendingSort() {
        var pageable = new PageInput(0, 10, "name", SortDirection.ASC).toPageable();

        Sort.Order order = pageable.getSort().getOrderFor("name");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void toPageable_withSortByAndNullDirection_defaultsToDescending() {
        var pageable = new PageInput(0, 10, "createdAt", null).toPageable();

        Sort.Order order = pageable.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageable_withBlankSortBy_isUnsorted() {
        var pageable = new PageInput(0, 10, "   ", SortDirection.ASC).toPageable();

        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    void toPageable_withNullPageAndSize_defaultsToZeroAndTwenty() {
        var pageable = new PageInput(null, null, null, null).toPageable();

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
    }

    @Test
    void staticToPageable_withNullInput_returnsUnsortedDefaultPage() {
        var pageable = PageInput.toPageable(null);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }
}
