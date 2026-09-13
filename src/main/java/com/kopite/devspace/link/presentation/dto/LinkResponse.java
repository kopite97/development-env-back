package com.kopite.devspace.link.presentation.dto;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record LinkResponse(
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(minimum="1",maximum="9007199254740991") long revision,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant createdAt,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant updatedAt,
    @Schema(maxLength=100) String label,@Schema(maxLength=300) String description,
    @Schema(maxLength=2000,format="uri") String url,
    @Schema(allowableValues={"all","unity","server"}) String scope,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,minimum="0",maximum="9007199254740991") long position) {
    public static LinkResponse from(LinkSnapshot l) {return new LinkResponse(l.id(),l.revision(),l.createdAt(),l.updatedAt(),l.label(),l.description(),l.url(),l.scope(),l.position());}
}
