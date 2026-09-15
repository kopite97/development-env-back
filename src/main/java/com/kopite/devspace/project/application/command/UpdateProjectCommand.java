package com.kopite.devspace.project.application.command;

import com.kopite.devspace.project.domain.ProjectValues;
import java.math.BigDecimal;

/** Only absent fields use null. The HTTP boundary rejects explicit null. */
public record UpdateProjectCommand(long revision, String name, String subtitle, String stack,
                                   BigDecimal progress, String currentMilestone, String repositoryUrl, String status,
                                   ProjectCategorySelection category) {
    public UpdateProjectCommand(long revision,String name,String subtitle,String stack,BigDecimal progress,String currentMilestone,String repositoryUrl,String status) {
        this(revision,name,subtitle,stack,progress,currentMilestone,repositoryUrl,status,ProjectCategorySelection.omitted());
    }
    public UpdateProjectCommand { java.util.Objects.requireNonNull(category); }
    public ProjectValues applyTo(ProjectValues current) {
        return new ProjectValues(name == null ? current.name() : name,
                subtitle == null ? current.subtitle() : subtitle,
                stack == null ? current.stack() : stack,
                progress == null ? current.progress() : progress,
                currentMilestone == null ? current.currentMilestone() : currentMilestone,
                repositoryUrl == null ? current.repositoryUrl() : repositoryUrl,
                category.present() ? category.id() : current.categoryId());
    }
}
