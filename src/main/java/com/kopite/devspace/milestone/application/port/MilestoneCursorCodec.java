package com.kopite.devspace.milestone.application.port;
import com.kopite.devspace.milestone.application.model.MilestoneCursor;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;
import java.util.UUID;
public interface MilestoneCursorCodec {
    String encode(UUID workspace, MilestoneListFilter filter, MilestoneCursor position);
    MilestoneCursor decode(UUID workspace, MilestoneListFilter filter, String cursor);
}
