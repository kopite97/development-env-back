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
    private final ProjectCategoryAggregation aggregate;
    private final com.kopite.devspace.projectcategory.application.CategoryFilterOwnership categoryOwnership;
    private final TaskQueryService tasks;
    public ProjectCategoryCountsSnapshot categoryCounts(UUID user) {
        var workspace=currentUser.resolve(user).workspace();
        return new ProjectCategoryCountsSnapshot(aggregate.counts(workspace.getId(),new OverviewFilter("all",null)),workspace.getDataRevision());
    }
    public OverviewSnapshot get(UUID user,OverviewFilter filter) {
        UUID workspace=currentUser.resolve(user).workspace().getId();
        categoryOwnership.validate(workspace,filter.category());
        if(filter.projectId()!=null) projects.findOwned(workspace,filter.projectId()).orElseThrow(ProjectNotFoundException::new);
        var counts=aggregate.counts(workspace,filter);
        // Task stats joins this transaction; its aggregation has no LIMIT or search restriction.
        var stats=tasks.stats(user,new TaskListFilter(filter.category(),filter.projectId(),"all","",null,false,20));
        return new OverviewSnapshot(filter,counts,stats);
    }
}
