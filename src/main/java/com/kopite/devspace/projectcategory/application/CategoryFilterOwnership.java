package com.kopite.devspace.projectcategory.application;
import com.kopite.devspace.projectcategory.domain.ProjectCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component @RequiredArgsConstructor
public class CategoryFilterOwnership {
    private final ProjectCategoryRepository categories;
    public void validate(UUID workspace,String selector) {
        UUID id=CategoryFilter.id(selector);
        if(id!=null)categories.findOwned(workspace,id).orElseThrow(CategoryNotFoundException::new);
    }
}
