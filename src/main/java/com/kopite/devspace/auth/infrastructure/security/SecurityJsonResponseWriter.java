package com.kopite.devspace.auth.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.UUID;

public final class SecurityJsonResponseWriter {

    private SecurityJsonResponseWriter() {
    }

    public static void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        String requestId = UUID.randomUUID().toString();
        response.getWriter().write("{\"code\":\""
                + escape(code)
                + "\",\"message\":\""
                + escape(message)
                + "\",\"fieldErrors\":{},\"requestId\":\""
                + requestId
                + "\"}");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
