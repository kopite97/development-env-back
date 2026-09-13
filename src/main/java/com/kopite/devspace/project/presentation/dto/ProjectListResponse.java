package com.kopite.devspace.project.presentation.dto;

import com.kopite.devspace.project.application.query.ProjectQueryService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(requiredProperties = {"items", "total", "nextCursor"})
public record ProjectListResponse(List<ProjectResponse> items,
                                  @Schema(description = "All matching owned rows, not the page size") long total,
                                  @Schema(nullable = true, description = "Null on the last page") String nextCursor) {
    public static ProjectListResponse from(ProjectQueryService.Page page) {
        return new ProjectListResponse(page.items().stream().map(ProjectResponse::from).toList(), page.total(), page.nextCursor());
    }
}
