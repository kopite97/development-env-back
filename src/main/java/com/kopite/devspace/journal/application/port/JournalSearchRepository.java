package com.kopite.devspace.journal.application.port;
import com.kopite.devspace.journal.application.model.JournalCursor;
import com.kopite.devspace.journal.application.model.JournalSnapshot;
import com.kopite.devspace.journal.application.query.JournalListFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface JournalSearchRepository {
    Optional<JournalSnapshot> findOwned(UUID workspace, UUID id);
    long count(UUID workspace, JournalListFilter filter);
    List<JournalSnapshot> page(UUID workspace, JournalListFilter filter, JournalCursor after);
}
