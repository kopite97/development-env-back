package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.model.LinkMutation;
import io.swagger.v3.oas.annotations.media.Schema;
public record LinkMutationResponse(LinkResponse item,@Schema(accessMode=Schema.AccessMode.READ_ONLY,minimum="0",maximum="9007199254740991") long collectionRevision) {
    public static LinkMutationResponse from(LinkMutation m){return new LinkMutationResponse(LinkResponse.from(m.item()),m.collectionRevision());}
}
