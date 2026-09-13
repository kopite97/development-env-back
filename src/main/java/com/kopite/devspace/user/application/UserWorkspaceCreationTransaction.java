package com.kopite.devspace.user.application;

import com.kopite.devspace.user.domain.AuthIdentity;
import com.kopite.devspace.user.domain.AuthIdentityId;
import com.kopite.devspace.user.domain.AuthIdentityRepository;
import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.user.domain.UserRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserWorkspaceCreationTransaction {

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PersonalWorkspaceRepository workspaceRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserWorkspaceCreationResult createOrReuse(AuthIdentityId identityId, String displayName) {
        return authIdentityRepository.findById(identityId)
                .map(this::reuseExistingIdentity)
                .orElseGet(() -> createNewUser(identityId, displayName));
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<UserWorkspaceCreationResult> findExisting(AuthIdentityId identityId) {
        return authIdentityRepository.findById(identityId)
                .map(this::reuseExistingIdentity);
    }

    private UserWorkspaceCreationResult reuseExistingIdentity(AuthIdentity identity) {
        User user = identity.getUser();
        PersonalWorkspace workspace = workspaceRepository.findByOwnerId(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "An existing identity must have an owned personal workspace"
                ));
        return new UserWorkspaceCreationResult(user, workspace);
    }

    private UserWorkspaceCreationResult createNewUser(AuthIdentityId identityId, String displayName) {
        User user = userRepository.save(User.create(displayName));
        AuthIdentity identity = authIdentityRepository.save(
                AuthIdentity.create(user, identityId.getIssuer(), identityId.getSubject())
        );
        PersonalWorkspace workspace = workspaceRepository.save(PersonalWorkspace.create(user));
        return new UserWorkspaceCreationResult(identity.getUser(), workspace);
    }
}
