package com.kopite.devspace.milestone.application.query;
import com.kopite.devspace.milestone.application.exception.MilestoneNotFoundException;
import com.kopite.devspace.milestone.application.model.MilestoneCursor;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import com.kopite.devspace.milestone.application.port.MilestoneCursorCodec;
import com.kopite.devspace.milestone.application.port.MilestoneSearchRepository;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.project.domain.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class MilestoneQueryService {
    private final CurrentUserService currentUser;
    private final com.kopite.devspace.projectcategory.application.CategoryFilterOwnership categoryOwnership;
    private final ProjectRepository projects;
    private final MilestoneSearchRepository search;
    private final MilestoneCursorCodec cursors;
    public record Page(List<MilestoneSnapshot> items, long total, String nextCursor, Long dataRevision) {}

    public MilestoneSnapshot get(UUID userId, UUID id) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        return search.findOwned(workspace, id).orElseThrow(MilestoneNotFoundException::new).observed(currentUser.resolve(userId).workspace().getDataRevision());
    }
    public Page list(UUID userId, MilestoneListFilter filter, String cursor) {
        UUID workspace = ownedWorkspace(userId, filter);
        var after = cursor == null ? null : cursors.decode(workspace, filter, cursor);
        long total = search.count(workspace, filter);
        var rows = search.page(workspace, filter, after);
        var items = rows.stream().limit(filter.limit()).toList();
        String next = null;
        if (rows.size() > filter.limit()) {
            var last = items.getLast();
            next = cursors.encode(workspace, filter, new MilestoneCursor(last.completed(), last.dueDate(), last.id()));
        }
        return new Page(items, total, next, currentUser.resolve(userId).workspace().getDataRevision());
    }
    private UUID ownedWorkspace(UUID userId, MilestoneListFilter filter) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        categoryOwnership.validate(workspace,filter.category());
        if (filter.projectId() != null) projects.findOwned(workspace, filter.projectId()).orElseThrow(MilestoneNotFoundException::new);
        return workspace;
    }
}
