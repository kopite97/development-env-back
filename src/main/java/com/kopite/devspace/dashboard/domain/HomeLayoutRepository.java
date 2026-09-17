package com.kopite.devspace.dashboard.domain;
import java.util.*;
public interface HomeLayoutRepository {
    Optional<HomeLayout> find(UUID workspace,boolean lock);
    List<WidgetPlacement> placements(UUID workspace);
    void insert(HomeLayout layout);
    void replacePlacements(UUID workspace,List<WidgetPlacement> placements);
    void flush();
}
