package com.kopite.devspace.workspace.infrastructure;

import com.kopite.devspace.workspace.domain.PersonalWorkspace;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface PersonalWorkspaceJpaRepository extends JpaRepository<PersonalWorkspace, UUID> {

    @EntityGraph(attributePaths = "owner")
    Optional<PersonalWorkspace> findByOwner_Id(UUID ownerId);
}
