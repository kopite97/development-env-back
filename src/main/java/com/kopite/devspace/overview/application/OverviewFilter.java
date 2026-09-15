package com.kopite.devspace.overview.application;
import java.util.Set;
import java.util.UUID;
public record OverviewFilter(String category,UUID projectId) {
    public OverviewFilter {
        category=com.kopite.devspace.projectcategory.application.CategoryFilter.normalize(category);
    }
}
