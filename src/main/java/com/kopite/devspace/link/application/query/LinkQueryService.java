package com.kopite.devspace.link.application.query;
import com.kopite.devspace.link.application.exception.LinkNotFoundException;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.port.LinkSearchRepository;
import com.kopite.devspace.link.domain.LinkCollection;
import com.kopite.devspace.link.domain.LinkCollectionRepository;
import com.kopite.devspace.link.domain.LinkRepository;
import com.kopite.devspace.auth.application.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class LinkQueryService {
    private final CurrentUserService currentUser;
    private final LinkRepository links;
    private final LinkCollectionRepository collections;
    private final LinkSearchRepository search;
    public record Listing(List<LinkSnapshot> items,long collectionRevision) {}
    public LinkSnapshot get(UUID user,UUID id) {
        UUID workspace=currentUser.resolve(user).workspace().getId();
        return LinkSnapshot.from(links.findOwned(workspace,id).orElseThrow(LinkNotFoundException::new));
    }
    public Listing list(UUID user,LinkListFilter filter) {
        UUID workspace=currentUser.resolve(user).workspace().getId();
        long revision=collections.find(workspace).map(LinkCollection::getRevision).orElse(0L);
        return new Listing(search.list(workspace,filter),revision);
    }
}
