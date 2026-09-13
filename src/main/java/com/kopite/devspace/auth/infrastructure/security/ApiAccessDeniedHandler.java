package com.kopite.devspace.auth.infrastructure.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        // CSRF can reject a request before authorization invokes the authentication entry point.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            authenticationEntryPoint.commence(request, response,
                    new InsufficientAuthenticationException("Authentication is required."));
            return;
        }
        boolean csrfFailure = exception instanceof CsrfException;
        SecurityJsonResponseWriter.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                csrfFailure ? "CSRF_INVALID" : "FORBIDDEN",
                csrfFailure ? "The CSRF token is invalid." : "Access is denied."
        );
    }
}
