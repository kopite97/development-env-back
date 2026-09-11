package com.kopite.devspace.user.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthIdentityId {

    @Column(name = "issuer", nullable = false, columnDefinition = "text")
    private String issuer;

    @Column(name = "subject", nullable = false, columnDefinition = "text")
    private String subject;

    public AuthIdentityId(String issuer, String subject) {
        this.issuer = requireNonEmpty(issuer, "issuer");
        this.subject = requireNonEmpty(subject, "subject");
    }

    private static String requireNonEmpty(String value, String fieldName) {
        String nonNullValue = Objects.requireNonNull(value, fieldName);
        if (nonNullValue.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be empty");
        }
        return nonNullValue;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthIdentityId that)) {
            return false;
        }
        return Objects.equals(issuer, that.issuer)
                && Objects.equals(subject, that.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(issuer, subject);
    }
}
