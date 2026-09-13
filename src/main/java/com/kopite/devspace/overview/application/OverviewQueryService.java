package com.kopite.devspace.overview.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.project.domain.ProjectRepository;
import com.kopite.devspace.project.application.exception.ProjectNotFoundException;
import com.kopite.devspace.task.application.query.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class OverviewQueryService {
    private final CurrentUserService currentUser;
    private final ProjectRepository projects;
    private final OverviewProjectRepository aggregate;
    private final TaskQueryService tasks;
    public OverviewSnapshot get(UUID user,OverviewFilter filter) {
        UUID workspace=currentUser.resolve(user).workspace().getId();
        if(filter.projectId()!=null) projects.findOwned(workspace,filter.projectId()).orElseThrow(ProjectNotFoundException::new);
        var counts=aggregate.counts(workspace,filter);
        // Task stats joins this transaction; its aggregation has no LIMIT or search restriction.
        var stats=tasks.stats(user,new TaskListFilter(filter.scope(),filter.projectId(),"all","",null,false,20));
        return new OverviewSnapshot(filter,counts,stats);
    }
}
