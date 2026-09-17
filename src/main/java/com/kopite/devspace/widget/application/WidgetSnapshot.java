package com.kopite.devspace.widget.application;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kopite.devspace.widget.domain.Widget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
public record WidgetSnapshot(UUID id,String type,String title,int configVersion,long revision,
    @Schema(oneOf={LocalWidgetConfig.class}) WidgetConfig config,
    @Schema(allowableValues={"valid","missingCategory"},accessMode=Schema.AccessMode.READ_ONLY) String referenceState,
    Instant createdAt,Instant updatedAt,@JsonIgnore Long dataRevision) {
    public static WidgetSnapshot from(Widget w,WidgetConfig c,boolean missing,Long revision) {
        return new WidgetSnapshot(w.getId(),w.getType(),w.getTitle(),w.getConfigVersion(),w.getRevision(),c,missing?"missingCategory":"valid",w.getCreatedAt(),w.getUpdatedAt(),revision);
    }
}
