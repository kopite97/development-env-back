package com.kopite.devspace.journal.infrastructure;
import com.kopite.devspace.journal.application.exception.InvalidJournalCursorException;
import com.kopite.devspace.journal.application.model.JournalCursor;
import com.kopite.devspace.journal.application.port.JournalCursorCodec;
import com.kopite.devspace.journal.application.query.JournalListFilter;

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
public class SignedJournalCursorCodec implements JournalCursorCodec {
    private final byte[] signingKey;

    public SignedJournalCursorCodec(@Value("${app.project.cursor-signing-key}") String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        if (this.signingKey.length < 32) throw new IllegalArgumentException("Journal cursor signing key must be at least 32 UTF-8 bytes");
    }

    @Override public String encode(UUID workspace, JournalListFilter filter, JournalCursor position) {
        String payload = "v2." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                (position.entryDate() + "/" + position.createdAt() + "/" + position.id()).getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(workspace, filter, payload));
    }

    @Override public JournalCursor decode(UUID workspace, JournalListFilter filter, String cursor) {
        try {
            if (cursor.length() > 512) throw new InvalidJournalCursorException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v2")) throw new InvalidJournalCursorException();
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(workspace, filter, parts[0] + "." + parts[1]), supplied)) throw new InvalidJournalCursorException();
            String[] position = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).split("/", -1);
            if (position.length != 3) throw new InvalidJournalCursorException();
            var decoded = new JournalCursor(java.time.LocalDate.parse(position[0]), Instant.parse(position[1]), UUID.fromString(position[2]));
            if (!encode(workspace, filter, decoded).equals(cursor)) throw new InvalidJournalCursorException();
            return decoded;
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new InvalidJournalCursorException();
        }
    }

    private byte[] sign(UUID workspace, JournalListFilter filter, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            // All context fields before query have fixed/enum syntax; query is length-prefixed to avoid ambiguous framing.
            String bound = "journals:" + workspace + ":" + filter.category() + ":" + filter.projectId() + ":" + filter.projectStatus() + ":" + filter.from() + ":" + filter.to() + ":" + filter.limit()
                    + ":" + filter.sort() + ":" + filter.query().length() + ":" + filter.query() + ":" + payload;
            return mac.doFinal(bound.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("Cannot sign journal cursor", impossible); }
    }
}
