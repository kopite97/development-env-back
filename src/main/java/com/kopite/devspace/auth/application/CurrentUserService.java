package com.kopite.devspace.auth.application;

import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.user.domain.UserRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private final UserRepository userRepository;
    private final PersonalWorkspaceRepository workspaceRepository;

    @Transactional(readOnly = true)
    public UserWorkspaceCreationResult resolve(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(NoSuchElementException::new);
        if (user.getDisabledAt() != null) {
            throw new AccountDisabledException();
        }
        PersonalWorkspace workspace = workspaceRepository.findByOwnerId(userId)
                .orElseThrow(NoSuchElementException::new);
        return new UserWorkspaceCreationResult(user, workspace);
    }
}
