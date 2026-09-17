package com.kopite.devspace.widget.application;
import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.*;

public record WidgetDataEnvelope(UUID widgetId,String type,long configRevision,int configVersion,int payloadVersion,
    @Schema(allowableValues={"ready","empty","unavailable"}) String availability,
    @Schema(allowableValues={"current","stale","unknown"}) String freshness,
    Instant readAt,@Schema(nullable=true) Instant sourceObservedAt,@Schema(nullable=true) Instant lastSuccessfulSyncAt,
    @Schema(nullable=true,discriminatorProperty="kind",oneOf={WidgetPayload.Overview.class,WidgetPayload.Board.class,WidgetPayload.Journal.class,WidgetPayload.Milestone.class,WidgetPayload.Links.class}) WidgetPayload data,
    @Schema(nullable=true) Page page,@Schema(nullable=true) Problem problem,@JsonIgnore Long dataRevision) {
    @Schema(name="WidgetDataPage") public record Page(long total,@Schema(nullable=true) String nextCursor){public Page{if(total<0)throw new IllegalArgumentException("Negative total");}}
    @Schema(name="WidgetDataProblem") public record Problem(String code,boolean retryable){public Problem{if(code==null||!code.matches("[A-Z][A-Z0-9_]*"))throw new IllegalArgumentException("Unsafe problem code");}}
    public WidgetDataEnvelope {
        Objects.requireNonNull(widgetId);Objects.requireNonNull(type);Objects.requireNonNull(readAt);
        if(configRevision<1||configVersion<1||payloadVersion<1)throw new IllegalArgumentException("Invalid observation versions");
        boolean content=("ready".equals(availability)||"empty".equals(availability))&&data!=null&&type.equals(data.kind())
            &&(("current".equals(freshness)&&problem==null)||"stale".equals(freshness));
        boolean unavailable="unavailable".equals(availability)&&"unknown".equals(freshness)&&data==null&&page==null&&problem!=null;
        if(!content&&!unavailable)throw new IllegalArgumentException("Invalid Widget data envelope combination");
    }
}
