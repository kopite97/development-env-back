package com.kopite.devspace.dashboard.presentation.dto;
import com.kopite.devspace.dashboard.application.HomeDashboardSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
@Schema(description="Configuration only, no business data. Unsaved GET returns deterministic defaults at revision 0 without creating state. Persisted revisions start at 1; saved empty widgets remain empty. Array order is layout order.")
public record HomeDashboardResponse(
    @Schema(allowableValues={"home"}) String id,
    @Schema(allowableValues={"1"}) int schemaVersion,
    @Schema(accessMode=Schema.AccessMode.READ_ONLY,minimum="0",maximum="9007199254740991") long revision,
    List<DashboardWidgetResponse> widgets) {
    public static HomeDashboardResponse from(HomeDashboardSnapshot d) {return new HomeDashboardResponse("home",1,d.revision(),d.widgets().stream().map(DashboardWidgetResponse::from).toList());}
}
