package com.kopite.devspace.task.application.port;
import com.kopite.devspace.task.application.model.TaskCursor;
import com.kopite.devspace.task.application.query.TaskListFilter;
import java.util.UUID;
public interface TaskCursorCodec {
    String encode(UUID workspace, TaskListFilter filter, TaskCursor position);
    TaskCursor decode(UUID workspace, TaskListFilter filter, String cursor);
}
