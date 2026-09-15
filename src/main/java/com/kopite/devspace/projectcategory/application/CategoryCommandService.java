package com.kopite.devspace.projectcategory.application;

import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.projectcategory.domain.*;
import com.kopite.devspace.workspace.domain.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service @RequiredArgsConstructor @Transactional
public class CategoryCommandService {
    private final CurrentUserService currentUser;
    private final PersonalWorkspaceRepository workspaces;
    private final ProjectCategoryRepository categories;
    private final CategoryCreateReplayStore replays;
    private final Clock projectClock;

    public CategorySnapshot create(UUID user,String key,String rawName) {
        var w=workspace(user);
        if(key==null || !key.matches("[!-~]{1,128}")) throw new CategoryValidationException("Idempotency-Key","must contain 1 to 128 visible ASCII characters");
        var name=new CategoryName(rawName);
        var now=projectClock.instant().truncatedTo(ChronoUnit.MICROS);
        String hash=CategoryRequestHash.of(rawName);
        var previous=replays.find(w.getId(),key);
        if(previous.isPresent() && previous.get().expiresAt().isAfter(now)) {
            if(!hash.equals(previous.get().requestHash())) throw new CategoryConflictException("IDEMPOTENCY_KEY_REUSED");
            return previous.get().result();
        }
        if(categories.count(w.getId())>=100) throw new CategoryQuotaException();
        uniqueName(w.getId(),name,null);
        capacity(w);
        var c=categories.save(ProjectCategory.create(w.getId(),name,now));
        w.recordBusinessMutation();categories.flush();
        var result=CategorySnapshot.from(c).observed(w.getDataRevision());
        replays.removeExpired(w.getId(),now);replays.save(w.getId(),key,hash,result,now);
        return result;
    }
    public CategorySnapshot rename(UUID user,UUID id,long revision,String rawName) {
        var w=workspace(user);
        var c=categories.lockOwned(w.getId(),id).orElseThrow(CategoryNotFoundException::new);
        c.checkRevision(revision);
        var name=new CategoryName(rawName);
        uniqueName(w.getId(),name,id);capacity(w);
        c.rename(revision,name,projectClock.instant());w.recordBusinessMutation();categories.flush();
        return CategorySnapshot.from(c).observed(w.getDataRevision());
    }
    public UUID delete(UUID user,UUID id,long revision) {
        var w=workspace(user);
        var c=categories.lockOwned(w.getId(),id).orElseThrow(CategoryNotFoundException::new);
        c.checkRevision(revision);
        if(categories.inUse(w.getId(),id)) throw new CategoryConflictException("CATEGORY_IN_USE");
        capacity(w);categories.delete(c);w.recordBusinessMutation();categories.flush();return id;
    }
    private PersonalWorkspace workspace(UUID user) {
        var w=workspaces.lockByOwnerId(user).orElseThrow(CategoryNotFoundException::new);
        currentUser.resolve(user);return w;
    }
    private void uniqueName(UUID workspace,CategoryName name,UUID excluding) {
        if(categories.nameExists(workspace,name.value(),excluding)) throw new CategoryConflictException("CATEGORY_NAME_CONFLICT");
    }
    private void capacity(PersonalWorkspace w) {
        if(w.getDataRevision()==Long.MAX_VALUE) throw new CategoryConflictException("REVISION_CONFLICT");
    }
    @Transactional
    public com.kopite.devspace.workspace.application.DeletedResource deleteObserved(UUID user,UUID id,long revision) {
        var deleted=delete(user,id,revision);
        return new com.kopite.devspace.workspace.application.DeletedResource(deleted,currentUser.resolve(user).workspace().getDataRevision());
    }

}
