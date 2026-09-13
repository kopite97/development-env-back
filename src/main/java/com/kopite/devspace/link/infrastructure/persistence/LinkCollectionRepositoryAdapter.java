package com.kopite.devspace.link.infrastructure.persistence;
import com.kopite.devspace.link.domain.LinkCollection;
import com.kopite.devspace.link.domain.LinkCollectionRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class LinkCollectionRepositoryAdapter implements LinkCollectionRepository {
    private final EntityManager em;
    public Optional<LinkCollection> find(UUID workspace) { return Optional.ofNullable(em.find(LinkCollection.class,workspace)); }
    public LinkCollection lockOrCreate(UUID workspace) {
        LinkCollection result=em.find(LinkCollection.class,workspace,LockModeType.PESSIMISTIC_WRITE);
        if (result==null) {
            result=LinkCollection.empty(workspace); em.persist(result); em.flush();
        }
        return result;
    }
}
