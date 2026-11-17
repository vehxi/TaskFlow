package ru.vehxi.taskflow.common.web;

import java.util.List;
import org.springframework.data.domain.Page;

/** A stable, paginated DTO envelope.
     * @param items visible items
     * @param page zero-based page
     * @param size page capacity
 *
     * @param totalElements matching item count
     * @param totalPages page count
 *
     * @param <T> public DTO type */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {
    /** Returns page envelope.
     * @param source mapped page
     * @param <T> public DTO type
     * @return page envelope */
    public static <T> PageResponse<T> from(Page<T> source) {
        return new PageResponse<>(source.getContent(), source.getNumber(), source.getSize(),
                source.getTotalElements(), source.getTotalPages());
    }
}
