package com.kopite.devspace.milestone.application.command;
import com.kopite.devspace.milestone.application.exception.MilestoneNotFoundException;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import com.kopite.devspace.milestone.application.port.MilestoneCreateReplayStore;
import com.kopite.devspace.milestone.domain.Milestone;
import com.kopite.devspace.milestone.domain.MilestoneConflictException;
import com.kopite.devspace.milestone.domain.MilestoneRepository;
import com.kopite.devspace.milestone.domain.MilestoneValidationException;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.project.domain.Project;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MilestoneCommandService {
    private final CurrentUserService currentUser;
    private final PersonalWorkspaceRepository workspaces;
    private final ProjectRepository projects;
    private final MilestoneRepository milestones;
    private final MilestoneCreateReplayStore replays;
    private final Clock projectClock;

    @Transactional
    public MilestoneSnapshot create(UUID userId,String key,CreateMilestoneCommand command) {
        var workspace=lockWorkspace(userId);
        if(key==null || !key.matches("[!-~]{1,128}"))
            throw new MilestoneValidationException("Idempotency-Key","must contain 1 to 128 visible ASCII characters");
        var values=command.values();
        Project project=projects.lockOwned(workspace.getId(),values.projectId()).orElseThrow(MilestoneNotFoundException::new);
        var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash=MilestoneRequestHash.of(command);
        var previous=replays.find(workspace.getId(),key);
        if(previous.isPresent() && previous.get().expiresAt().isAfter(now)) {
            var saved=previous.get();
            projects.findOwned(workspace.getId(),saved.result().projectId()).orElseThrow(MilestoneNotFoundException::new);
            milestones.findOwned(workspace.getId(),saved.result().id()).ifPresent(milestone ->
                projects.findOwned(workspace.getId(),milestone.getProjectId()).orElseThrow(MilestoneNotFoundException::new));
            if(!saved.requestHash().equals(hash)) throw new MilestoneConflictException("IDEMPOTENCY_KEY_REUSED");
            return saved.result();
        }
        replays.removeExpired(workspace.getId(),now);
        Milestone milestone=milestones.save(Milestone.create(workspace.getId(),values,now));
        workspace.recordBusinessMutation();
        var result=MilestoneSnapshot.from(milestone,project);
        replays.save(workspace.getId(),key,hash,result,now);
        return result;
    }
    @Transactional
    public MilestoneSnapshot update(UUID userId,UUID id,UpdateMilestoneCommand command) {
        var workspace=lockWorkspace(userId);
        var milestone=lockMilestone(workspace.getId(),id,command.projectId());
        milestone.checkRevision(command.revision());
        var values=command.applyTo(milestone.values());
        var target=projects.findOwned(workspace.getId(),values.projectId()).orElseThrow(MilestoneNotFoundException::new);
        milestone.update(command.revision(),values,projectClock.instant());
        workspace.recordBusinessMutation();
        return MilestoneSnapshot.from(milestone,target);
    }
    @Transactional
    public UUID delete(UUID userId,UUID id,long revision) {
        var workspace=lockWorkspace(userId);
        var milestone=lockMilestone(workspace.getId(),id,null);
        milestone.checkRevision(revision);
        milestones.delete(milestone);
        workspace.recordBusinessMutation();
        return id;
    }
    private Milestone lockMilestone(UUID workspace,UUID id,UUID target) {
        var discovered=milestones.findOwned(workspace,id).orElseThrow(MilestoneNotFoundException::new);
        Stream.of(discovered.getProjectId(),target==null?discovered.getProjectId():target).distinct().sorted()
            .forEach(project->projects.lockOwned(workspace,project).orElseThrow(MilestoneNotFoundException::new));
        return milestones.lockOwned(workspace,id).orElseThrow(MilestoneNotFoundException::new);
    }
    private PersonalWorkspace lockWorkspace(UUID userId) {
        var workspace=workspaces.lockByOwnerId(userId).orElseThrow(MilestoneNotFoundException::new);
        currentUser.resolve(userId);
        return workspace;
    }
}
