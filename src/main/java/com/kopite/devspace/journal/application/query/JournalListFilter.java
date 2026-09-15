package com.kopite.devspace.journal.application.query;
import com.kopite.devspace.journal.domain.JournalValidationException;
import java.util.UUID;
public record JournalListFilter(String category, UUID projectId, String projectStatus, String query,
                             java.time.LocalDate from, java.time.LocalDate to, String sort, int limit) {
    public JournalListFilter {
        category = com.kopite.devspace.projectcategory.application.CategoryFilter.normalize(category);
        if (!"all".equals(projectStatus) && !"active".equals(projectStatus) && !"archived".equals(projectStatus)) invalid("projectStatus");
        if (!"newest".equals(sort) && !"oldest".equals(sort)) invalid("sort");
        if (from != null && to != null && from.isAfter(to)) invalid("from");
        if (query == null) invalid("query");
        if (limit < 1 || limit > 100) invalid("limit");
    }
    private static void invalid(String field) { throw new JournalValidationException(field, "invalid filter value"); }
}
