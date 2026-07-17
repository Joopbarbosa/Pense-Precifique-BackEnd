package com.penseprecifique.api.shared.dto.response;

import java.util.List;

public record PageResponseDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {}
