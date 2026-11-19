package com.changeguard.controlplane.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every request has a valid UUID correlation id in MDC and echoes it
 * on the response together with the API version header (docs/API_DESIGN.md §1-2).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_HEADER = "X-Correlation-ID";
    public static final String API_VERSION_HEADER = "X-API-Version";
    public static final String MDC_KEY = "correlationId";
    public static final String API_VERSION = "v1";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(CORRELATION_HEADER, correlationId);
        response.setHeader(API_VERSION_HEADER, API_VERSION);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String incoming = request.getHeader(CORRELATION_HEADER);
        if (incoming != null && !incoming.isBlank()) {
            try {
                return UUID.fromString(incoming.trim()).toString();
            } catch (IllegalArgumentException ignored) {
                // Non-UUID inbound values are replaced with a fresh UUID.
            }
        }
        return UUID.randomUUID().toString();
    }
}
