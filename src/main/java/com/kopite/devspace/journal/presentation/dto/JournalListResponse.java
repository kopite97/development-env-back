package com.kopite.devspace.journal.presentation.dto;
import com.kopite.devspace.journal.application.query.JournalQueryService;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
public record JournalListResponse(List<JournalResponse> items,long total,
    @Schema(types={"string","null"}) String nextCursor) {
    public static JournalListResponse from(JournalQueryService.Page page) {
        return new JournalListResponse(page.items().stream().map(JournalResponse::from).toList(),page.total(),page.nextCursor());
    }
}
