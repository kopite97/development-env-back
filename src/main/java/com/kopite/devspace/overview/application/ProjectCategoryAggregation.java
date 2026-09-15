package com.kopite.devspace.overview.application;

import com.kopite.devspace.projectcategory.application.CategoryFilter;
import com.kopite.devspace.projectcategory.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;

/** Called within the caller's repeatable-read transaction. No Project collection is loaded. */
@Service
@RequiredArgsConstructor
public class ProjectCategoryAggregation {
    private final OverviewProjectRepository projects;
    private final ProjectCategoryRepository categories;

    public List<ProjectCategoryCount> counts(UUID workspace, OverviewFilter filter) {
        var buckets = new LinkedHashMap<UUID,long[]>();
        if ("all".equals(filter.category())) {
            categories.list(workspace).forEach(c -> buckets.put(c.getId(),new long[2]));
            buckets.put(null,new long[2]);
        } else buckets.put(CategoryFilter.id(filter.category()),new long[2]);
        for (var count : projects.counts(workspace,filter)) {
            var bucket = buckets.get(count.categoryId());
            if (bucket == null) throw new IllegalStateException("Aggregate outside owned Category collection");
            bucket["active".equals(count.status()) ? 0 : 1] += count.count();
        }
        return buckets.entrySet().stream()
            .map(e -> new ProjectCategoryCount(e.getKey(),e.getValue()[0],e.getValue()[1])).toList();
    }
}
