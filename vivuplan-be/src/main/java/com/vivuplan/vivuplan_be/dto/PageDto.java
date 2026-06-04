package com.vivuplan.vivuplan_be.dto;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

public class PageDto {

    @Data
    public static class PageResponse<T> {
        private List<T> content;
        private long totalElements;
        private int totalPages;
        private int size;
        private int number;
        private boolean first;
        private boolean last;

        public static <T> PageResponse<T> from(Page<T> page) {
            PageResponse<T> response = new PageResponse<>();
            response.setContent(page.getContent());
            response.setTotalElements(page.getTotalElements());
            response.setTotalPages(page.getTotalPages());
            response.setSize(page.getSize());
            response.setNumber(page.getNumber());
            response.setFirst(page.isFirst());
            response.setLast(page.isLast());
            return response;
        }
    }
}
