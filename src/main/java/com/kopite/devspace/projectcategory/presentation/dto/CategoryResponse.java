package com.kopite.devspace.projectcategory.presentation.dto;
import com.kopite.devspace.projectcategory.application.CategorySnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
@Schema(requiredProperties={"id","name","revision","createdAt","updatedAt"},additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record CategoryResponse(@Schema(accessMode=Schema.AccessMode.READ_ONLY) UUID id,
    @Schema(minLength=1,maxLength=100) String name,
    @Schema(minimum="1",maximum="9007199254740991",accessMode=Schema.AccessMode.READ_ONLY) long revision,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant createdAt,@Schema(accessMode=Schema.AccessMode.READ_ONLY) Instant updatedAt) {
    public static CategoryResponse from(CategorySnapshot c){return new CategoryResponse(c.id(),c.name(),c.revision(),c.createdAt(),c.updatedAt());}
}
