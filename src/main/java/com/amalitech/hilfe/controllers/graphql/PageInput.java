package com.amalitech.hilfe.controllers.graphql;

import com.amalitech.hilfe.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

public record PageInput(Integer page, Integer size) {

    public Pageable toPageable() {
        return PageRequest.of(page != null ? page : 0, size != null ? size : 20);
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
