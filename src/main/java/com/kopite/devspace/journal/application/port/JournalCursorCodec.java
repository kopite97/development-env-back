package com.kopite.devspace.journal.application.port;
import com.kopite.devspace.journal.application.model.JournalCursor;
import com.kopite.devspace.journal.application.query.JournalListFilter;
import java.util.UUID;
public interface JournalCursorCodec {
    String encode(UUID workspace, JournalListFilter filter, JournalCursor position);
    JournalCursor decode(UUID workspace, JournalListFilter filter, String cursor);
}
