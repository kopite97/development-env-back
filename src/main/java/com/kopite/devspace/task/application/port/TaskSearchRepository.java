package com.kopite.devspace.task.application.port;
import com.kopite.devspace.task.application.model.TaskCursor;
import com.kopite.devspace.task.application.model.TaskSnapshot;
import com.kopite.devspace.task.application.query.TaskListFilter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
public interface TaskSearchRepository {
    Optional<TaskSnapshot> findOwned(UUID workspace, UUID id);
    long count(UUID workspace, TaskListFilter filter);
    List<TaskSnapshot> page(UUID workspace, TaskListFilter filter, TaskCursor after);
    Map<String, Long> counts(UUID workspace, TaskListFilter filter);
}
