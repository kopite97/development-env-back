package com.kopite.devspace.projectcategory.application;
import java.util.List;
public record CategoryCollection(List<CategorySnapshot> items,long dataRevision) {
    public CategoryCollection { items=List.copyOf(items); }
}
