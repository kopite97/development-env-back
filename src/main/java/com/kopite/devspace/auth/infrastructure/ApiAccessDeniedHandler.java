package com.kopite.devspace.auth.infrastructure;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException, ServletException {
        boolean csrfFailure = exception instanceof CsrfException;
        SecurityJsonResponseWriter.write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                csrfFailure ? "CSRF_INVALID" : "FORBIDDEN",
                csrfFailure ? "The CSRF token is invalid." : "Access is denied."
        );
    }
}
