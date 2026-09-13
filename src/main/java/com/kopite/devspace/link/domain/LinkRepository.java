package com.kopite.devspace.link.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LinkRepository {
    Link save(Link link);
    void delete(Link link);
    Optional<Link> findOwned(UUID workspace, UUID id);
    Optional<Link> lockOwned(UUID workspace, UUID id);
    List<Link> lockAll(UUID workspace);
    long count(UUID workspace);
    long maxPosition(UUID workspace);
    void beginReorder();
    void finishReorder();
}
