package com.kopite.devspace.project.application.query;

import com.kopite.devspace.project.domain.ProjectValidationException;
import java.util.Set;

public record ProjectListFilter(String scope, String status, String query, int limit) {
    public ProjectListFilter {
        if (!Set.of("all", "unity", "server").contains(scope)) throw new ProjectValidationException("scope", "invalid scope");
        if (!Set.of("all", "active", "archived").contains(status)) throw new ProjectValidationException("status", "invalid status");
        if (limit < 1 || limit > 100) throw new ProjectValidationException("limit", "must be between 1 and 100");
        if (query == null) throw new ProjectValidationException("query", "must not be null");
    }
}
