package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.domain.DashboardWidget;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DashboardWidgetResponse(
    @Schema(minLength=1) String id,
    @Schema(allowableValues={"overview","board","deploy","links","journal","milestone"}) String type,
    @Schema(minLength=1,maxLength=48) String title,
    @Schema(allowableValues={"all","unity","server"},description="Always all with projectId") String scope,
    @Schema(allowableValues={"small","medium","wide"}) String size,
    UUID projectId,@Schema(minimum="1",maximum="20") Integer limit) {
    public static DashboardWidgetResponse from(DashboardWidget v){return new DashboardWidgetResponse(v.id(),v.type(),v.title(),v.scope(),v.size(),v.projectId(),v.limit());}
}
