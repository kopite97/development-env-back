package com.kopite.devspace.project.domain;

import java.math.BigDecimal;
import java.net.URI;

/** Complete, validated editable values; transport omission is resolved before construction. */
public record ProjectValues(String name, String subtitle, String scope, String stack,
                            BigDecimal progress, String currentMilestone, String repositoryUrl) {
    public ProjectValues {
        name = text("name", name, 100, true, true);
        subtitle = text("subtitle", subtitle, 4000, false, false);
        stack = text("stack", stack, 200, true, true);
        currentMilestone = text("currentMilestone", currentMilestone, 200, false, false);
        repositoryUrl = text("repositoryUrl", repositoryUrl, 2000, false, true);
        if (!"unity".equals(scope) && !"server".equals(scope)) {
            throw new ProjectValidationException("scope", "must be unity or server");
        }
        if (progress == null || progress.signum() < 0 || progress.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ProjectValidationException("progress", "must be a number between 0 and 100");
        }
        if (!repositoryUrl.isEmpty()) {
            try {
                URI uri = URI.create(repositoryUrl);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException ex) {
                throw new ProjectValidationException("repositoryUrl", "must be an absolute http or https URL with a host");
            }
        }
    }

    private static String text(String field, String value, int max, boolean required, boolean trim) {
        if (value == null) throw new ProjectValidationException(field, "must not be null");
        String normalized = trim ? value.trim() : value;
        if (required && normalized.isBlank()) throw new ProjectValidationException(field, "must not be blank");
        if (normalized.length() > max) throw new ProjectValidationException(field, "must be at most " + max + " UTF-16 code units");
        return normalized;
    }
}
