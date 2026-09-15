package com.kopite.devspace.overview.application;
import java.util.List;
public record ProjectCategoryCountsSnapshot(List<ProjectCategoryCount> items, long dataRevision) {
    public ProjectCategoryCountsSnapshot { items=List.copyOf(items); }
}
