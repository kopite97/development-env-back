package com.kopite.devspace.link.presentation.dto;
import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import com.kopite.devspace.link.application.model.LinkSnapshot;
@Schema(description="Entire filtered collection; never paginated. At most 500 items and 8 MiB compact UTF-8. collectionRevision describes the global owned collection.")
public record LinkListResponse(@ArraySchema(maxItems=500) List<LinkResponse> items,long total,
    @Schema(types={"null"},description="Always null; no cursor pagination") String nextCursor,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,minimum="0",maximum="9007199254740991") long collectionRevision) {
    public static LinkListResponse from(List<LinkSnapshot> items,long revision){return new LinkListResponse(items.stream().map(LinkResponse::from).toList(),items.size(),null,revision);}
}
