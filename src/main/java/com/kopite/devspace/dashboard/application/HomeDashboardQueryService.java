package com.kopite.devspace.dashboard.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.dashboard.domain.*;
import com.kopite.devspace.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class HomeDashboardQueryService {
    private final CurrentUserService currentUser;
    private final HomeDashboardRepository dashboards;
    private final DashboardReferences references;
    public HomeDashboardSnapshot get(UUID user) {
        UUID workspace=currentUser.resolve(user).workspace().getId();
        var saved=dashboards.find(workspace);
        if(saved.isEmpty()) return new HomeDashboardSnapshot(0,HomeDashboard.defaults()).observed(java.util.Set.of(),currentUser.resolve(user).workspace().getDataRevision());
        var result=HomeDashboardSnapshot.from(saved.get());
        return result.observed(references.missingCategories(workspace,result.widgets()),currentUser.resolve(user).workspace().getDataRevision());
    }
}
