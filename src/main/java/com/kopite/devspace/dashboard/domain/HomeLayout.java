package com.kopite.devspace.dashboard.domain;
import com.kopite.devspace.widget.domain.Widget;
import com.kopite.devspace.widget.domain.WidgetException;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Current layout only. Legacy JSONB columns remain frozen recovery data. */
@Entity @Table(name="dashboards") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class HomeLayout {
    @Embeddable public record Key(UUID workspaceId,String dashboardKey) implements Serializable {}
    @EmbeddedId private Key id;
    @Column(nullable=false) private long layoutRevision;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false,updatable=false) private Instant updatedAt;
    @Column(nullable=false) private Instant layoutUpdatedAt;
    public static HomeLayout create(UUID workspace,Instant now) {
        var d=new HomeLayout();d.id=new Key(workspace,"home");d.layoutRevision=1;
        d.createdAt=now.truncatedTo(ChronoUnit.MICROS);d.updatedAt=d.createdAt;d.layoutUpdatedAt=d.createdAt;return d;
    }
    public void check(long expected) {Widget.revision(expected,true);if(expected!=layoutRevision||layoutRevision==Widget.MAX_REVISION)throw WidgetException.conflict();}
    public void advance(long expected,Instant now){check(expected);layoutRevision++;layoutUpdatedAt=now.truncatedTo(ChronoUnit.MICROS);}
}
