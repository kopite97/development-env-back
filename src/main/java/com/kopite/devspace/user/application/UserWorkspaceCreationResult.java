package com.kopite.devspace.user.application;

import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;

public record UserWorkspaceCreationResult(User user, PersonalWorkspace workspace) {
}
