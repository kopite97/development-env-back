package com.kopite.devspace.project.presentation.dto;
import com.kopite.devspace.overview.application.ProjectCategoryCountsSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;
public record ProjectCategoryCountsResponse(List<Item> items, Totals totals) {
    @Schema(name="ProjectCategoryCountItem")
    public record Item(@Schema(types={"string","null"},format="uuid") UUID categoryId,
                       @Schema(minimum="0") long active,@Schema(minimum="0") long archived) {}
    @Schema(name="ProjectCategoryCountTotals")
    public record Totals(@Schema(minimum="0") long active,@Schema(minimum="0") long archived) {}
    public static ProjectCategoryCountsResponse from(ProjectCategoryCountsSnapshot snapshot) {
        var items=snapshot.items().stream().map(c -> new Item(c.categoryId(),c.active(),c.archived())).toList();
        return new ProjectCategoryCountsResponse(items,new Totals(items.stream().mapToLong(Item::active).sum(),items.stream().mapToLong(Item::archived).sum()));
    }
}
