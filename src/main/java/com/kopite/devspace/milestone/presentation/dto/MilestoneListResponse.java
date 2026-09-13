package com.kopite.devspace.milestone.presentation.dto;
import com.kopite.devspace.milestone.application.query.MilestoneQueryService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
public record MilestoneListResponse(List<MilestoneResponse> items,long total,
    @Schema(types={"string","null"}) String nextCursor) {
    public static MilestoneListResponse from(MilestoneQueryService.Page page) {
        return new MilestoneListResponse(page.items().stream().map(MilestoneResponse::from).toList(),page.total(),page.nextCursor());
    }
}
