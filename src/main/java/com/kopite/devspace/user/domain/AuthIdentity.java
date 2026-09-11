package com.kopite.devspace.user.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Table(name = "auth_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthIdentity {

    @EmbeddedId
    private AuthIdentityId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_auth_identities_user")
    )
    private User user;

    private AuthIdentity(AuthIdentityId id, User user) {
        this.id = Objects.requireNonNull(id, "id");
        this.user = Objects.requireNonNull(user, "user");
    }

    public static AuthIdentity create(User user, String issuer, String subject) {
        return new AuthIdentity(new AuthIdentityId(issuer, subject), user);
    }

}
