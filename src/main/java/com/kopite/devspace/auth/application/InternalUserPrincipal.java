package com.kopite.devspace.auth.application;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * The small, serializable identity stored in the servlet security context.
 * Persistence entities and provider tokens never become session state.
 */
public record InternalUserPrincipal(UUID userId, String displayName) implements Serializable {

    public InternalUserPrincipal {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(displayName, "displayName");
    }
}
