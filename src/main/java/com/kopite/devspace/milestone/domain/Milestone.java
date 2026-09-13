package com.kopite.devspace.milestone.domain;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="milestones")
@Getter
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class Milestone {
    public static final long MAX_REVISION=9007199254740991L;
    @Id private UUID id;
    @Column(nullable=false,updatable=false) private UUID workspaceId;
    @Column(nullable=false) private UUID projectId;
    @Column(nullable=false,columnDefinition="text") private String title;
    @Column(nullable=false) private boolean completed;
    private LocalDate dueDate;
    @Column(nullable=false) private long revision;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    @Column(nullable=false) private Instant updatedAt;

    public static Milestone create(UUID workspace,MilestoneValues values,Instant now) {
        Milestone milestone=new Milestone();
        milestone.id=UUID.randomUUID();
        milestone.workspaceId=Objects.requireNonNull(workspace);
        milestone.assign(values);
        milestone.revision=1;
        milestone.createdAt=timestamp(now);
        milestone.updatedAt=milestone.createdAt;
        return milestone;
    }
    public void checkRevision(long expected) {
        if(expected<1 || expected>MAX_REVISION) throw new MilestoneValidationException("revision","must be a positive safe integer");
        if(expected!=revision) throw new MilestoneConflictException("REVISION_CONFLICT");
    }
    public void update(long expected,MilestoneValues values,Instant now) {
        checkRevision(expected);
        if(revision==MAX_REVISION) throw new MilestoneConflictException("REVISION_CONFLICT");
        Objects.requireNonNull(values);
        Instant time=timestamp(now);
        assign(values);
        revision++;
        updatedAt=time;
    }
    public MilestoneValues values() { return new MilestoneValues(title,projectId,dueDate,completed); }
    private static Instant timestamp(Instant now) { return Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS); }
    private void assign(MilestoneValues values) {
        Objects.requireNonNull(values);
        title=values.title(); projectId=values.projectId(); dueDate=values.dueDate(); completed=values.completed();
    }
}
