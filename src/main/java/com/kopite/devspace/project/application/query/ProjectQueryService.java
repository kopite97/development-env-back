package com.kopite.devspace.project.application.query;
import com.kopite.devspace.project.application.exception.ProjectNotFoundException;
import com.kopite.devspace.project.application.model.ProjectCursor;
import com.kopite.devspace.project.application.model.ProjectSnapshot;
import com.kopite.devspace.project.application.port.ProjectCursorCodec;
import com.kopite.devspace.project.application.port.ProjectSearchRepository;

import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProjectQueryService {
    private final CurrentUserService currentUser;
    private final ProjectRepository projects;
    private final ProjectSearchRepository search;
    private final ProjectCursorCodec cursors;

    public record Page(List<ProjectSnapshot> items, long total, String nextCursor) {}

    @Transactional(readOnly = true)
    public ProjectSnapshot get(UUID userId, UUID id) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        return ProjectSnapshot.from(projects.findOwned(workspace, id).orElseThrow(ProjectNotFoundException::new));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page list(UUID userId, ProjectListFilter filter, String cursor) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        var after = cursor == null ? null : cursors.decode(workspace, filter, cursor);
        long total = search.count(workspace, filter);
        var rows = search.page(workspace, filter, after);
        boolean more = rows.size() > filter.limit();
        var items = rows.stream().limit(filter.limit()).map(ProjectSnapshot::from).toList();
        String next = null;
        if (more) {
            var last = items.getLast();
            next = cursors.encode(workspace, filter, new ProjectCursor(last.createdAt(), last.id()));
        }
        return new Page(items, total, next);
    }
}
