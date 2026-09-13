package com.kopite.devspace.overview.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
public record OverviewTaskCounts(@Schema(minimum="0") long todo,@Schema(minimum="0") long doing,@Schema(minimum="0") long done,@Schema(minimum="0") long total) {}
