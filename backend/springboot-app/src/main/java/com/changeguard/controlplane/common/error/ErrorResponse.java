package com.changeguard.controlplane.common.error;

import java.util.List;

/**
 * Canonical error envelope (docs/API_DESIGN.md §3).
 */
public record ErrorResponse(ErrorBody error) {

    public record ErrorBody(
            String code,
            String message,
            List<ErrorDetail> details,
            String correlationId,
            java.time.Instant timestamp) {

        public ErrorBody {
            if (details == null) {
                details = List.of();
            }
        }
    }

    public record ErrorDetail(String field, String issue) {
    }

    public static ErrorResponse of(String code, String message, List<ErrorDetail> details,
                                   String correlationId, java.time.Instant timestamp) {
        return new ErrorResponse(new ErrorBody(code, message, details, correlationId, timestamp));
    }
}
