package com.kopite.devspace.user.application;

import com.kopite.devspace.user.domain.AuthIdentityId;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserWorkspaceCreationService {

    private final UserWorkspaceCreationTransaction transaction;

    public UserWorkspaceCreationResult createOrReuse(String issuer, String subject, String displayName) {
        AuthIdentityId identityId = new AuthIdentityId(issuer, subject);

        try {
            return transaction.createOrReuse(identityId, displayName);
        } catch (DataIntegrityViolationException creationFailure) {
            // The proxied creation transaction has fully rolled back before a fresh
            // transaction reads the winner, including when the caller has a transaction.
            return transaction.findExisting(identityId)
                    .orElseThrow(() -> creationFailure);
        }
    }
}
