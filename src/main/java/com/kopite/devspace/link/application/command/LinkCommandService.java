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
    private final com.kopite.devspace.project.domain.ProjectRepository projects;
    private final LinkCreateReplayStore replays;
    private final LinkLimits limits;
    private final Clock projectClock;
    public record Deleted(UUID deletedId,long collectionRevision,long dataRevision) {}
    public record Ordered(List<LinkSnapshot> items,long collectionRevision,long dataRevision) {}

    public LinkMutation create(UUID user,String key,CreateLinkCommand command) {
        var w=workspace(user);
        if(key==null || !key.matches("[!-~]{1,128}")) throw new LinkValidationException("Idempotency-Key","must contain 1 to 128 visible ASCII characters");
        var values=command.values();
        var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash=LinkRequestHash.of(command);
        var previous=replays.find(w.getId(),key);
        if(previous.isPresent() && previous.get().expiresAt().isAfter(now)) {
            if(previous.get().legacy() || !hash.equals(previous.get().requestHash())) throw new LinkConflictException("IDEMPOTENCY_KEY_REUSED");
            return previous.get().result();
        }
        var target=values.projectId()==null?null:projects.lockOwned(w.getId(),values.projectId()).orElseThrow(LinkNotFoundException::new);
        var c=collections.lockOrCreate(w.getId());
        if(links.count(w.getId())>=limits.getMaxLinks()) throw new LinkQuotaException(limits.getMaxLinks());
        c.checkCanAdvance();
        long position=Link.nextPosition(links.maxPosition(w.getId()));
        w.recordBusinessMutation();c.advance();
        var link=links.save(Link.create(w.getId(),values,position,now));
        var result=new LinkMutation(LinkSnapshot.from(link,target),c.getRevision()).observed(w.getDataRevision());
        replays.removeExpired(w.getId(),now);replays.save(w.getId(),key,hash,result,now);
        return result;
    }
    public LinkMutation update(UUID user,UUID id,UpdateLinkCommand command) {
        var w=workspace(user);
        var observed=links.findOwned(w.getId(),id).orElseThrow(LinkNotFoundException::new);
        java.util.stream.Stream.of(observed.getProjectId(),command.project().present()?command.project().id():null)
            .filter(Objects::nonNull).distinct().sorted().forEach(p->projects.lockOwned(w.getId(),p).orElseThrow(LinkNotFoundException::new));
        var c=collections.lockOrCreate(w.getId());
        var l=links.lockOwned(w.getId(),id).orElseThrow(LinkNotFoundException::new);
        l.checkRevision(command.revision());l.checkCanAdvance();c.checkCanAdvance();
        var values=command.applyTo(l.values());
        w.recordBusinessMutation();c.advance();l.update(command.revision(),values,projectClock.instant());
        return new LinkMutation(snapshot(l),c.getRevision()).observed(w.getDataRevision());
    }
    public Deleted delete(UUID user,UUID id,long revision) {
        var w=workspace(user);var c=collections.lockOrCreate(w.getId());
        var l=links.lockOwned(w.getId(),id).orElseThrow(LinkNotFoundException::new);
        l.checkRevision(revision);c.checkCanAdvance();
        w.recordBusinessMutation();c.advance();links.delete(l);
        return new Deleted(id,c.getRevision(),w.getDataRevision());
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
        var referenced=projects.findOwnedByIds(w.getId(),rows.stream().map(Link::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(com.kopite.devspace.project.domain.Project::getId,Function.identity()));
        return new Ordered(ids.stream().map(byId::get).map(l->LinkSnapshot.from(l,referenced.get(l.getProjectId()))).toList(),c.getRevision(),w.getDataRevision());
    }
    private LinkSnapshot snapshot(Link l) {
        return LinkSnapshot.from(l,l.getProjectId()==null?null:projects.findOwned(l.getWorkspaceId(),l.getProjectId()).orElseThrow(LinkNotFoundException::new));
    }
    private PersonalWorkspace workspace(UUID user) {
        var w=workspaces.lockByOwnerId(user).orElseThrow(LinkNotFoundException::new);
        currentUser.resolve(user);return w;
    }
}
