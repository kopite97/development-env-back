package com.kopite.devspace.link.application.command;
import com.kopite.devspace.link.application.LinkLimits;
import com.kopite.devspace.link.application.exception.LinkNotFoundException;
import com.kopite.devspace.link.application.exception.LinkQuotaException;
import com.kopite.devspace.link.application.model.LinkMutation;
import com.kopite.devspace.link.application.model.LinkSnapshot;
import com.kopite.devspace.link.application.port.LinkCreateReplayStore;
import com.kopite.devspace.link.domain.Link;
import com.kopite.devspace.link.domain.LinkCollectionRepository;
import com.kopite.devspace.link.domain.LinkConflictException;
import com.kopite.devspace.link.domain.LinkRepository;
import com.kopite.devspace.link.domain.LinkValidationException;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;

import com.kopite.devspace.auth.application.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class LinkCommandService {
    private final CurrentUserService currentUser;
    private final PersonalWorkspaceRepository workspaces;
    private final LinkCollectionRepository collections;
    private final LinkRepository links;
    private final LinkCreateReplayStore replays;
    private final LinkLimits limits;
    private final Clock projectClock;
    public record Deleted(UUID deletedId,long collectionRevision) {}
    public record Ordered(List<LinkSnapshot> items,long collectionRevision) {}

    public LinkMutation create(UUID user,String key,CreateLinkCommand command) {
        var w=workspace(user);
        if(key==null || !key.matches("[!-~]{1,128}")) throw new LinkValidationException("Idempotency-Key","must contain 1 to 128 visible ASCII characters");
        var values=command.values();
        var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash=LinkRequestHash.of(command);
        var previous=replays.find(w.getId(),key);
        if(previous.isPresent() && previous.get().expiresAt().isAfter(now)) {
            if(!hash.equals(previous.get().requestHash())) throw new LinkConflictException("IDEMPOTENCY_KEY_REUSED");
            return previous.get().result();
        }
        var c=collections.lockOrCreate(w.getId());
        if(links.count(w.getId())>=limits.getMaxLinks()) throw new LinkQuotaException(limits.getMaxLinks());
        c.checkCanAdvance();
        long position=Link.nextPosition(links.maxPosition(w.getId()));
        w.recordBusinessMutation();c.advance();
        var link=links.save(Link.create(w.getId(),values,position,now));
        var result=new LinkMutation(LinkSnapshot.from(link),c.getRevision());
        replays.removeExpired(w.getId(),now);replays.save(w.getId(),key,hash,result,now);
        return result;
    }
    public LinkMutation update(UUID user,UUID id,UpdateLinkCommand command) {
        var w=workspace(user);var c=collections.lockOrCreate(w.getId());
        var l=links.lockOwned(w.getId(),id).orElseThrow(LinkNotFoundException::new);
        l.checkRevision(command.revision());l.checkCanAdvance();c.checkCanAdvance();
        var values=command.applyTo(l.values());
        w.recordBusinessMutation();c.advance();l.update(command.revision(),values,projectClock.instant());
        return new LinkMutation(LinkSnapshot.from(l),c.getRevision());
    }
    public Deleted delete(UUID user,UUID id,long revision) {
        var w=workspace(user);var c=collections.lockOrCreate(w.getId());
        var l=links.lockOwned(w.getId(),id).orElseThrow(LinkNotFoundException::new);
        l.checkRevision(revision);c.checkCanAdvance();
        w.recordBusinessMutation();c.advance();links.delete(l);
        return new Deleted(id,c.getRevision());
    }
    public Ordered reorder(UUID user,long expected,List<UUID> ids) {
        var w=workspace(user);var c=collections.lockOrCreate(w.getId());c.checkRevision(expected);
        var rows=links.lockAll(w.getId());
        if(ids==null || ids.stream().anyMatch(Objects::isNull) || ids.size()!=rows.size()
                || new HashSet<>(ids).size()!=ids.size()
                || !new HashSet<>(ids).equals(rows.stream().map(Link::getId).collect(Collectors.toSet())))
            throw new LinkValidationException("ids","must be an exact permutation of all owned Link IDs");
        c.checkCanAdvance();
        var byId=rows.stream().collect(Collectors.toMap(Link::getId,Function.identity()));
        for(int i=0;i<ids.size();i++) if(byId.get(ids.get(i)).getPosition()!=i) byId.get(ids.get(i)).checkCanAdvance();
        w.recordBusinessMutation();c.advance();links.beginReorder();
        var now=projectClock.instant();
        for(int i=0;i<ids.size();i++) byId.get(ids.get(i)).move(i,now);
        links.finishReorder();
        return new Ordered(ids.stream().map(byId::get).map(LinkSnapshot::from).toList(),c.getRevision());
    }
    private PersonalWorkspace workspace(UUID user) {
        var w=workspaces.lockByOwnerId(user).orElseThrow(LinkNotFoundException::new);
        currentUser.resolve(user);return w;
    }
}
