package com.kopite.devspace.link.presentation.dto;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;
public record DeleteLinkResponse(UUID deletedId,@Schema(accessMode=Schema.AccessMode.READ_ONLY,minimum="0",maximum="9007199254740991") long collectionRevision) {}
