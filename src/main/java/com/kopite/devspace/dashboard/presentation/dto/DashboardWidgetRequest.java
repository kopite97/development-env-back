package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.DashboardWidget;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.UUID;
@Schema(additionalProperties=Schema.AdditionalPropertiesValue.FALSE,description="ID unique within the dashboard after trim; repeated types allowed. Optional settings must be omitted, never null. All sizes allowed for every type.")
public record DashboardWidgetRequest(
    @NotBlank String id,
    @NotNull @Schema(allowableValues={"overview","board","deploy","links","journal","milestone"}) String type,
    @NotBlank @Size(max=48) String title,
    @NotNull @Schema(allowableValues={"all","unity","server"}) String scope,
    @NotNull @Schema(allowableValues={"small","medium","wide"}) String size,
    @Schema(description="Only overview/board/journal/milestone; owned active or archived Project. Normalizes scope to all.") UUID projectId,
    @Min(1) @Max(20) @Schema(description="Only overview/board/journal/milestone. Omitted: all via paging for overview/board, journal 3, milestone 2. Board limit covers all columns together; statistics are independent.") Integer limit) {
    public DashboardWidget value(){return new DashboardWidget(id,type,title,scope,size,projectId,limit);}
    static DashboardWidgetRequest from(DashboardWidget v){return new DashboardWidgetRequest(v.id(),v.type(),v.title(),v.scope(),v.size(),v.projectId(),v.limit());}
}
