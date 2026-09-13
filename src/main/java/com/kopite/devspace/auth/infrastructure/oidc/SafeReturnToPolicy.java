package com.kopite.devspace.auth.infrastructure.oidc;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Accepts only local application paths for post-login redirects.
 */
@Component
public class SafeReturnToPolicy {

    public String normalize(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "/";
        }

        String value = candidate.trim();
        String decoded;
        try {
            decoded = URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return "/";
        }

        if (!value.startsWith("/")
                || value.startsWith("//")
                || value.contains("\\")
                || value.contains("#")
                || decoded.startsWith("//")
                || decoded.contains("\\")
                || decoded.contains("://")
                || decoded.contains("\r")
                || decoded.contains("\n")
                || decoded.startsWith("/api/")) {
            return "/";
        }

        try {
            URI uri = URI.create(value);
            if (uri.isAbsolute() || uri.getHost() != null || uri.getScheme() != null) {
                return "/";
            }
        } catch (IllegalArgumentException exception) {
            return "/";
        }
        return value;
    }
}
