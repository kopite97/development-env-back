package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.DashboardWidget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="ID unique after trim; repeated types allowed. Selection required. Optional limit must be omitted, never null. All sizes allowed.")
public record DashboardWidgetRequest(
    @NotBlank String id,
    @NotNull @Schema(allowableValues={"overview","board","deploy","links","journal","milestone"}) String type,
    @NotBlank @Size(max=48) String title,
    @NotNull @Schema(allowableValues={"small","medium","wide"}) String size,
    @NotNull @Valid DashboardSelectionDto selection,
    @Min(1) @Max(20) @Schema(description="Only overview/board/journal/milestone; no numeric default inserted. Board limit covers all columns; statistics independent.") Integer limit) {
    public DashboardWidget value(){return new DashboardWidget(id,type,title,size,selection.value(),limit);}
    static DashboardWidgetRequest from(DashboardWidget v){return new DashboardWidgetRequest(v.id(),v.type(),v.title(),v.size(),DashboardSelectionDto.from(v.selection()),v.limit());}
}
