package com.project.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", requestId);
        response.setHeader("X-Request-Id", requestId);

        long start = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - start;
            String method = request.getMethod();
            String uri = request.getRequestURI();
            int status = response.getStatus();

            if (duration >= SLOW_REQUEST_THRESHOLD_MS) {
                log.warn("SLOW [{}] {} {} → {} ({}ms)", requestId, method, uri, status, duration);
            } else if (status >= 500) {
                log.error("[{}] {} {} → {} ({}ms)", requestId, method, uri, status, duration);
            } else if (status >= 400) {
                log.warn("[{}] {} {} → {} ({}ms)", requestId, method, uri, status, duration);
            } else {
                log.debug("[{}] {} {} → {} ({}ms)", requestId, method, uri, status, duration);
            }

            MDC.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator");
    }
}
