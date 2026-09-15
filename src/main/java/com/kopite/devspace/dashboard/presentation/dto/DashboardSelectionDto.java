package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.DashboardSelection;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="Exactly one selection shape: all, uncategorized, project plus projectId, or category plus categoryId. No null fields or mixed identities.")
public record DashboardSelectionDto(@Schema(allowableValues={"all","uncategorized","project","category"}) String kind,UUID projectId,UUID categoryId) {
    public DashboardSelection value(){return new DashboardSelection(kind,projectId,categoryId);}
    public static DashboardSelectionDto from(DashboardSelection value){return new DashboardSelectionDto(value.kind(),value.projectId(),value.categoryId());}
}
