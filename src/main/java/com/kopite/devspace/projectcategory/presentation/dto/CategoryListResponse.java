package com.kopite.devspace.projectcategory.presentation.dto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(requiredProperties={"items","total"},additionalProperties=Schema.AdditionalPropertiesValue.FALSE)
public record CategoryListResponse(List<CategoryResponse> items,@Schema(minimum="0",description="Full collection size; creation admission limit 100 never truncates existing data") long total) {}
