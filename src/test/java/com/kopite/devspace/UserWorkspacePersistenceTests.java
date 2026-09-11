package com.kopite.devspace;

import com.kopite.devspace.user.application.UserWorkspaceCreationResult;
import com.kopite.devspace.user.application.UserWorkspaceCreationService;
import com.kopite.devspace.user.domain.AuthIdentity;
import com.kopite.devspace.user.domain.AuthIdentityId;
import com.kopite.devspace.user.domain.AuthIdentityRepository;
import com.kopite.devspace.user.domain.User;
import com.kopite.devspace.user.domain.UserRepository;
import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import com.kopite.devspace.workspace.domain.PersonalWorkspaceRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@SpringBootTest
class UserWorkspacePersistenceTests {

    private final UserWorkspaceCreationService creationService;

    private final UserRepository userRepository;

    private final AuthIdentityRepository authIdentityRepository;

    private final PersonalWorkspaceRepository workspaceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    UserWorkspacePersistenceTests(
            UserWorkspaceCreationService creationService,
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PersonalWorkspaceRepository workspaceRepository
    ) {
        this.creationService = creationService;
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Test
    @Transactional
    void createsAndReloadsUserIdentityAndWorkspace() {
        String issuer = "issuer-" + System.nanoTime();
        String subject = "subject-" + System.nanoTime();

        UserWorkspaceCreationResult created = creationService.createOrReuse(
                issuer,
                subject,
                "  First User  "
        );

        entityManager.flush();
        entityManager.clear();

        User user = userRepository.findById(created.user().getId()).orElseThrow();
        AuthIdentity identity = authIdentityRepository
                .findById(new AuthIdentityId(issuer, subject))
                .orElseThrow();
        PersonalWorkspace workspace = workspaceRepository
                .findById(created.workspace().getId())
                .orElseThrow();

        assertEquals(created.user().getId(), user.getId());
        assertEquals("First User", user.getDisplayName());
        assertNotNull(user.getCreatedAt());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        assertNull(user.getDisabledAt());
        assertEquals(issuer, identity.getId().getIssuer());
        assertEquals(subject, identity.getId().getSubject());
        assertEquals(user.getId(), identity.getUser().getId());
        assertEquals(user.getId(), workspace.getOwner().getId());
        assertEquals(PersonalWorkspace.DEFAULT_NAME, workspace.getName());
        assertEquals(1, workspace.getRevision());
        assertEquals(0, workspace.getDataRevision());
        assertEquals(workspace.getCreatedAt(), workspace.getUpdatedAt());
    }

    @Test
    @Transactional
    void reusesExistingIdentityAndOwnedWorkspace() {
        String issuer = "issuer-" + System.nanoTime();
        String subject = "subject-" + System.nanoTime();

        UserWorkspaceCreationResult first = creationService.createOrReuse(
                issuer,
                subject,
                "First User"
        );
        entityManager.flush();
        entityManager.clear();

        UserWorkspaceCreationResult second = creationService.createOrReuse(
                issuer,
                subject,
                "Different Display Name"
        );

        assertEquals(first.user().getId(), second.user().getId());
        assertEquals(first.workspace().getId(), second.workspace().getId());
        assertEquals("First User", second.user().getDisplayName());
        assertTrue(authIdentityRepository.findById(new AuthIdentityId(issuer, subject)).isPresent());
        assertTrue(workspaceRepository.findByOwnerId(first.user().getId()).isPresent());
    }
}
