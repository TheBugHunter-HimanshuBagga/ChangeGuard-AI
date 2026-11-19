package com.changeguard.controlplane.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable machine error codes with their HTTP mapping (docs/API_DESIGN.md §3.1).
 * Service-specific codes may extend the initial set while keeping the same envelope.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    CONFLICT(HttpStatus.CONFLICT),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    SEMANTIC_REJECTED(HttpStatus.UNPROCESSABLE_ENTITY),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    UPSTREAM_BAD_RESPONSE(HttpStatus.BAD_GATEWAY),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT),

    // Service-specific
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    USER_DISABLED(HttpStatus.FORBIDDEN),
    ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),
    LAST_OWNER_REMOVAL(HttpStatus.UNPROCESSABLE_ENTITY);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
