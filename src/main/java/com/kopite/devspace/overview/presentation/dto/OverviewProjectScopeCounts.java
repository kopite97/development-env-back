package com.kopite.devspace.overview.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
public record OverviewProjectScopeCounts(@Schema(minimum="0",description="Active Projects") long total,@Schema(minimum="0") long archived) {}
