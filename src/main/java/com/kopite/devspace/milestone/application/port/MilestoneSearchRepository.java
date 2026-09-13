package com.kopite.devspace.milestone.application.port;
import com.kopite.devspace.milestone.application.model.MilestoneCursor;
import com.kopite.devspace.milestone.application.model.MilestoneSnapshot;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface MilestoneSearchRepository {
    Optional<MilestoneSnapshot> findOwned(UUID workspace, UUID id);
    long count(UUID workspace, MilestoneListFilter filter);
    List<MilestoneSnapshot> page(UUID workspace, MilestoneListFilter filter, MilestoneCursor after);
}
