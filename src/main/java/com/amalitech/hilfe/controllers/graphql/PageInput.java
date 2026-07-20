package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public record PageInput(Integer page, Integer size, String sortBy, SortDirection sortDirection) {

    public Pageable toPageable() {
        Sort.Direction direction = sortDirection == SortDirection.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = (sortBy != null && !sortBy.isBlank()) ? Sort.by(direction, sortBy) : Sort.unsorted();
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20, sort);
    }

    public static Pageable toPageable(PageInput input) {
        if (input == null) return PageRequest.of(0, 20);
        return input.toPageable();
    }

    public static <T> PageResponse<T> toPageResponse(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.hasNext(),
                page.hasPrevious()
        );
    }
}
