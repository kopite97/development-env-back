package com.kopite.devspace.auth.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

@Component
public class OriginValidationFilter extends OncePerRequestFilter {

    private final String allowedOrigin;

    public OriginValidationFilter(
            @Value("${app.security.origin:http://localhost:8080}") String allowedOrigin
    ) {
        this.allowedOrigin = normalizeOrigin(allowedOrigin);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (isAuthenticatedMutation(request)
                && !isAllowedOrigin(request.getHeader("Origin"))) {
            SecurityJsonResponseWriter.write(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "CSRF_INVALID",
                    "The request origin is not allowed."
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAuthenticatedMutation(HttpServletRequest request) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if (method.equals("GET") || method.equals("HEAD") || method.equals("OPTIONS")) {
            return false;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    private boolean isAllowedOrigin(String origin) {
        return origin == null || origin.isBlank() || allowedOrigin.equals(normalizeOrigin(origin));
    }

    private static String normalizeOrigin(String origin) {
        if (origin == null) {
            return "";
        }
        String normalized = origin.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
