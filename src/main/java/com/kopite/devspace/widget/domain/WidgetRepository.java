package com.kopite.devspace.widget.domain;
import java.time.Instant;
import java.util.*;
public interface WidgetRepository {
    Optional<Widget> owned(UUID workspace,UUID id,boolean lock);
    List<Widget> owned(UUID workspace,Set<UUID> ids);
    List<Widget> page(UUID workspace,boolean unplaced,int limit,Instant afterTime,UUID afterId);
    void insert(Widget widget);
    void delete(Widget widget);
    boolean placed(UUID workspace,UUID id);
    void flush();
}
