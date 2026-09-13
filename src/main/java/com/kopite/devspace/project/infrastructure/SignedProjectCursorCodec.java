package com.kopite.devspace.project.infrastructure;
import com.kopite.devspace.project.application.exception.InvalidProjectCursorException;
import com.kopite.devspace.project.application.model.ProjectCursor;
import com.kopite.devspace.project.application.port.ProjectCursorCodec;
import com.kopite.devspace.project.application.query.ProjectListFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class SignedProjectCursorCodec implements ProjectCursorCodec {
    private final byte[] signingKey;

    public SignedProjectCursorCodec(@Value("${app.project.cursor-signing-key}") String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        if (this.signingKey.length < 32) throw new IllegalArgumentException("Project cursor signing key must be at least 32 UTF-8 bytes");
    }

    @Override public String encode(UUID workspace, ProjectListFilter filter, ProjectCursor position) {
        String payload = "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                (position.createdAt() + "/" + position.id()).getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(workspace, filter, payload));
    }

    @Override public ProjectCursor decode(UUID workspace, ProjectListFilter filter, String cursor) {
        try {
            if (cursor.length() > 512) throw new InvalidProjectCursorException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new InvalidProjectCursorException();
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(workspace, filter, parts[0] + "." + parts[1]), supplied)) throw new InvalidProjectCursorException();
            String[] position = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).split("/", -1);
            if (position.length != 2) throw new InvalidProjectCursorException();
            var decoded = new ProjectCursor(Instant.parse(position[0]), UUID.fromString(position[1]));
            if (!encode(workspace, filter, decoded).equals(cursor)) throw new InvalidProjectCursorException();
            return decoded;
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new InvalidProjectCursorException();
        }
    }

    private byte[] sign(UUID workspace, ProjectListFilter filter, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            // All context fields before query have fixed/enum syntax; query is length-prefixed to avoid ambiguous framing.
            String bound = workspace + ":" + filter.scope() + ":" + filter.status() + ":" + filter.limit()
                    + ":createdAt-desc,id-desc:" + filter.query().length() + ":" + filter.query() + ":" + payload;
            return mac.doFinal(bound.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("Cannot sign project cursor", impossible); }
    }
}
