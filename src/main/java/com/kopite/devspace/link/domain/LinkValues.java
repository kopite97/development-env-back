package com.kopite.devspace.link.domain;

import java.net.URI;

public record LinkValues(String label, String description, String url, java.util.UUID projectId) {
    public LinkValues {
        label = text("label", label, 100, true);
        description = text("description", description, 300, false);
        url = text("url", url, 2000, true);
        try {
            URI uri = URI.create(url);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new LinkValidationException("url", "must be an absolute http/https URL with a host and no credentials");
        }
    }
    private static String text(String field, String value, int max, boolean required) {
        if (value == null) throw new LinkValidationException(field, "must not be null");
        String normalized = required ? value.trim() : value;
        if (required && normalized.isBlank()) throw new LinkValidationException(field, "must not be blank");
        if (normalized.length() > max) throw new LinkValidationException(field, "exceeds " + max + " UTF-16 code units");
        return normalized;
    }
}
