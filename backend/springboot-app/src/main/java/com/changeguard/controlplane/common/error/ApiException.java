package com.changeguard.controlplane.common.error;

import java.util.List;

/**
 * Application-level exception carrying a stable {@link ErrorCode}.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final List<ErrorResponse.ErrorDetail> details;

    public ApiException(ErrorCode code, String message) {
        this(code, message, List.of());
    }

    public ApiException(ErrorCode code, String message, List<ErrorResponse.ErrorDetail> details) {
        super(message);
        this.code = code;
        this.details = details == null ? List.of() : List.copyOf(details);
    }

    public ErrorCode code() {
        return code;
    }

    public List<ErrorResponse.ErrorDetail> details() {
        return details;
    }
}
