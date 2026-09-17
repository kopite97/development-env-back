package com.kopite.devspace.widget.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Entity @Table(name="widgets") @Getter @NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Widget {
    public static final long MAX_REVISION=9007199254740991L;
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private UUID workspaceId;
    @Column(nullable=false,updatable=false) private String type;
    @Column(nullable=false) private String title;
    @Column(nullable=false) private int configVersion;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable=false,columnDefinition="jsonb") private String config;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;
    public static Widget create(UUID workspace,String type,String title,int version,String validatedConfig,Instant now) {
        var w=new Widget();w.id=UUID.randomUUID();w.workspaceId=Objects.requireNonNull(workspace);
        if(type==null||!type.matches("[a-z][a-z0-9.-]*"))throw WidgetException.invalid("type");
        w.type=type;w.assign(title,version,validatedConfig);w.revision=1;
        w.createdAt=now.truncatedTo(ChronoUnit.MICROS);w.updatedAt=w.createdAt;return w;
    }
    public static String title(String value) {
        if(value==null||value.trim().isBlank()||value.trim().length()>48)throw WidgetException.invalid("title");
        return value.trim();
    }
    public static void revision(long value,boolean zero) {
        if(value<(zero?0:1)||value>MAX_REVISION)throw WidgetException.invalid("revision");
    }
    public void check(long expected) {revision(expected,false);if(expected!=revision||revision==MAX_REVISION)throw WidgetException.conflict();}
    public void replace(long expected,String title,int version,String config,Instant now) {
        check(expected);assign(title,version,config);revision++;updatedAt=now.truncatedTo(ChronoUnit.MICROS);
    }
    private void assign(String value,int version,String json) {
        title=title(value);if(version<1||json==null||!json.startsWith("{"))throw WidgetException.invalid("config");
        configVersion=version;config=json;
    }
}
