package com.kopite.devspace.milestone.application.command;
import com.kopite.devspace.milestone.domain.MilestoneValues;
import java.util.UUID;
/** Only dueDate permits explicit null; other nulls represent omitted fields. */
public record UpdateMilestoneCommand(long revision,String title,UUID projectId,String dueDate,Boolean completed,boolean dueDatePresent) {
    public MilestoneValues applyTo(MilestoneValues old) {
        return new MilestoneValues(title==null?old.title():title,projectId==null?old.projectId():projectId,
            !dueDatePresent?old.dueDate():dueDate==null?null:MilestoneValues.date(dueDate,"dueDate"),
            completed==null?old.completed():completed);
    }
}
