package com.kopite.devspace.link.domain;
import java.util.Optional;
import java.util.UUID;

public interface LinkCollectionRepository {
    Optional<LinkCollection> find(UUID workspace);
    LinkCollection lockOrCreate(UUID workspace);
}
