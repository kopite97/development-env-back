package com.kopite.devspace.overview.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
public record OverviewProjectCounts(@Schema(minimum="0",description="Active only, not active plus archived") long total,@Schema(minimum="0") long archived,OverviewProjectsByScope byScope) {}
