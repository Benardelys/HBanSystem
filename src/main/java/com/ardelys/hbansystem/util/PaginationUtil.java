package com.ardelys.hbansystem.util;

import java.util.Collections;
import java.util.List;

public final class PaginationUtil {

    private PaginationUtil() {}

    public static int getTotalPages(int totalItems, int pageSize) {
        if (totalItems <= 0 || pageSize <= 0) {
            return 1;
        }
        return (int) Math.ceil((double) totalItems / (double) pageSize);
    }

    public static <T> List<T> getPage(List<T> list, int page, int pageSize) {
        if (list == null || list.isEmpty() || pageSize <= 0) {
            return Collections.emptyList();
        }
        int totalPages = getTotalPages(list.size(), pageSize);
        int validPage = Math.max(1, Math.min(page, totalPages));
        int fromIndex = (validPage - 1) * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, list.size());
        if (fromIndex >= list.size()) {
            return Collections.emptyList();
        }
        return list.subList(fromIndex, toIndex);
    }
}
