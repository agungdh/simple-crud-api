package com.example.demo.user;

import java.util.List;
import java.util.UUID;

public record CursorResponse<T>(
        List<T> data,
        PaginationMeta pagination
) {
    public record PaginationMeta(
            int limit,
            boolean hasNext,
            UUID nextCursor
    ) {}
}
