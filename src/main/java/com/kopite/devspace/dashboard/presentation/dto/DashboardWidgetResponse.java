package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.DashboardWidget;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardWidgetResponse(
    @Schema(minLength=1) String id,
    @Schema(allowableValues={"overview","board","deploy","links","journal","milestone"}) String type,
    @Schema(minLength=1,maxLength=48) String title,
    @Schema(allowableValues={"small","medium","wide"}) String size,
    DashboardSelectionDto selection,
    @Schema(minimum="1",maximum="20") Integer limit,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,allowableValues={"valid","missingCategory"}) String selectionState) {
    public static DashboardWidgetResponse from(DashboardWidget v,boolean missing){return new DashboardWidgetResponse(v.id(),v.type(),v.title(),v.size(),DashboardSelectionDto.from(v.selection()),v.limit(),missing?"missingCategory":"valid");}
}
