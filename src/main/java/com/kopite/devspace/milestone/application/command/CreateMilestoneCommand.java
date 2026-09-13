package com.kopite.devspace.milestone.application.command;
import com.kopite.devspace.milestone.domain.MilestoneValues;
import java.util.UUID;
/** dueDatePresent preserves explicit null versus omission for creation fingerprints. */
public record CreateMilestoneCommand(String title,UUID projectId,String dueDate,Boolean completed,boolean dueDatePresent) {
    public MilestoneValues values() {
        return new MilestoneValues(title,projectId,dueDate==null?null:MilestoneValues.date(dueDate,"dueDate"),Boolean.TRUE.equals(completed));
    }
}
