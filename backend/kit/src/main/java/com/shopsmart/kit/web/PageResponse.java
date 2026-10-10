package com.shopsmart.kit.web;

import java.util.List;

public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int pages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, pages);
    }
}
