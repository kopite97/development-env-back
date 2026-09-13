package com.kopite.devspace.project.application.command;

import com.kopite.devspace.project.domain.ProjectValues;
import java.math.BigDecimal;

/** Null optional values mean omitted; explicit transport nulls must be rejected at the boundary. */
public record CreateProjectCommand(String name, String subtitle, String scope, String stack,
                                   BigDecimal progress, String currentMilestone, String repositoryUrl) {
    public ProjectValues values() {
        return new ProjectValues(name, subtitle == null ? "" : subtitle, scope, stack,
                progress == null ? BigDecimal.ZERO : progress,
                currentMilestone == null ? "" : currentMilestone, repositoryUrl == null ? "" : repositoryUrl);
    }
}
