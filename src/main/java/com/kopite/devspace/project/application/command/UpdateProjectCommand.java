package com.kopite.devspace.project.application.command;

import com.kopite.devspace.project.domain.ProjectValues;
import java.math.BigDecimal;

/** Only absent fields use null. The HTTP boundary rejects explicit null. */
public record UpdateProjectCommand(long revision, String name, String subtitle, String scope, String stack,
                                   BigDecimal progress, String currentMilestone, String repositoryUrl, String status) {
    public ProjectValues applyTo(ProjectValues current) {
        return new ProjectValues(name == null ? current.name() : name,
                subtitle == null ? current.subtitle() : subtitle,
                scope == null ? current.scope() : scope,
                stack == null ? current.stack() : stack,
                progress == null ? current.progress() : progress,
                currentMilestone == null ? current.currentMilestone() : currentMilestone,
                repositoryUrl == null ? current.repositoryUrl() : repositoryUrl);
    }
}
