package com.kopite.devspace.overview.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
public record OverviewProjectCategoryCounts(
    @Schema(types={"string","null"},format="uuid") UUID categoryId,
    @Schema(minimum="0",description="Active Projects") long total,
    @Schema(minimum="0") long archived) {}
