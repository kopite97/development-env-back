package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
@Service
@RequiredArgsConstructor
@Transactional
public class HomeDashboardCommandService {
    private final CurrentUserService currentUser;
    private final PersonalWorkspaceRepository workspaces;
    private final HomeDashboardRepository dashboards;
    private final DashboardReferences references;
    private final Clock projectClock;
    public HomeDashboardSnapshot save(UUID user,long expected,List<DashboardWidget> widgets) {
        HomeDashboard.validateRevision(expected);
        var values=HomeDashboard.validate(widgets);
        // Lock before loading Workspace state so a waiting command reads the winner's counter.
        var workspace=workspaces.lockByOwnerId(user).orElseThrow(DashboardNotFoundException::new);
        currentUser.resolve(user);
        var saved=dashboards.lock(workspace.getId());
        if(saved.isPresent()) saved.get().validateStored();
        var missing=references.validateSave(workspace.getId(),values,saved.map(HomeDashboard::getWidgets).orElse(List.of()));
        long revision=saved.map(HomeDashboard::getRevision).orElse(0L);
        if(expected!=revision || revision==HomeDashboard.MAX_REVISION || workspace.getDataRevision()==Long.MAX_VALUE)
            throw new DashboardConflictException();
        var now=projectClock.instant();
        HomeDashboard dashboard;
        if(saved.isEmpty()) {dashboard=HomeDashboard.create(workspace.getId(),values,now);dashboards.insert(dashboard);}
        else {dashboard=saved.get();dashboard.replace(expected,values,now);}
        workspace.recordBusinessMutation();
        dashboards.flush();
        return HomeDashboardSnapshot.from(dashboard).observed(missing,workspace.getDataRevision());
    }
}
