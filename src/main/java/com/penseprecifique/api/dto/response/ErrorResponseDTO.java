package com.penseprecifique.api.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

public record ErrorResponseDTO(
        String message,
        int status,
        LocalDateTime timestamp,
        Map<String, String> fieldErrors
) {}
