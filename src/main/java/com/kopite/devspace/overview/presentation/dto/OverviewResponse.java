package com.kopite.devspace.overview.presentation.dto;
import com.kopite.devspace.overview.application.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record OverviewResponse(
    @Schema(allowableValues={"all","unity","server"}) String scope,
    @Schema(types={"string","null"},format="uuid") UUID projectId,
    OverviewProjectCounts projects,OverviewTaskCounts tasks,
    @Schema(description="Server observation time within the read snapshot, not a commit watermark") Instant asOf) {
    public static OverviewResponse from(OverviewSnapshot s) {
        var unity=counts(s,"unity");var server=counts(s,"server");var t=s.tasks();
        return new OverviewResponse(s.filter().scope(),s.filter().projectId(),
            new OverviewProjectCounts(unity.total()+server.total(),unity.archived()+server.archived(),new OverviewProjectsByScope(unity,server)),
            new OverviewTaskCounts(t.counts().get("todo"),t.counts().get("doing"),t.counts().get("done"),t.total()),t.asOf());
    }
    private static OverviewProjectScopeCounts counts(OverviewSnapshot s,String scope) {
        long active=0,archived=0;
        for(var c:s.projects())if(c.scope().equals(scope)){if(c.status().equals("active"))active+=c.count();else archived+=c.count();}
        return new OverviewProjectScopeCounts(active,archived);
    }
}
