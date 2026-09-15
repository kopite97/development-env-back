package com.kopite.devspace.projectcategory.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@Schema(requiredProperties={"deletedId"},additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record DeleteCategoryResponse(UUID deletedId) {}
