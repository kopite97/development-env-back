package com.kopite.devspace.journal.application.query;
import com.kopite.devspace.journal.application.exception.JournalNotFoundException;
import com.kopite.devspace.journal.application.model.JournalCursor;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import com.kopite.devspace.journal.application.port.JournalCursorCodec;
import com.kopite.devspace.journal.application.port.JournalSearchRepository;
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
public class JournalQueryService {
    private final CurrentUserService currentUser;
    private final ProjectRepository projects;
    private final JournalSearchRepository search;
    private final JournalCursorCodec cursors;
    public record Page(List<JournalSnapshot> items, long total, String nextCursor) {}

    public JournalSnapshot get(UUID userId, UUID id) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        return search.findOwned(workspace, id).orElseThrow(JournalNotFoundException::new);
    }
    public Page list(UUID userId, JournalListFilter filter, String cursor) {
        UUID workspace = ownedWorkspace(userId, filter);
        var after = cursor == null ? null : cursors.decode(workspace, filter, cursor);
        long total = search.count(workspace, filter);
        var rows = search.page(workspace, filter, after);
        var items = rows.stream().limit(filter.limit()).toList();
        String next = null;
        if (rows.size() > filter.limit()) {
            var last = items.getLast();
            next = cursors.encode(workspace, filter, new JournalCursor(last.entryDate(), last.createdAt(), last.id()));
        }
        return new Page(items, total, next);
    }
    private UUID ownedWorkspace(UUID userId, JournalListFilter filter) {
        UUID workspace = currentUser.resolve(userId).workspace().getId();
        if (filter.projectId() != null) projects.findOwned(workspace, filter.projectId()).orElseThrow(JournalNotFoundException::new);
        return workspace;
    }
}
