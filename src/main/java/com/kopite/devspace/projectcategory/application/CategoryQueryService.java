package com.kopite.devspace.projectcategory.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.projectcategory.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
@Service @RequiredArgsConstructor @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class CategoryQueryService {
    private final CurrentUserService currentUser;
    private final ProjectCategoryRepository categories;
    public CategorySnapshot get(UUID user,UUID id) {
        var workspace=currentUser.resolve(user).workspace();
        return CategorySnapshot.from(categories.findOwned(workspace.getId(),id).orElseThrow(CategoryNotFoundException::new)).observed(workspace.getDataRevision());
    }
    public List<CategorySnapshot> list(UUID user) {
        return categories.list(currentUser.resolve(user).workspace().getId()).stream().map(CategorySnapshot::from).toList();
    }
    public CategoryCollection collection(UUID user) {
        var workspace=currentUser.resolve(user).workspace();
        return new CategoryCollection(list(user),workspace.getDataRevision());
    }
}
