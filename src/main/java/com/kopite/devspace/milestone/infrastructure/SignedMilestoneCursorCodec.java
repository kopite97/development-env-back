package com.kopite.devspace.milestone.infrastructure;
import com.kopite.devspace.milestone.application.exception.InvalidMilestoneCursorException;
import com.kopite.devspace.milestone.application.model.MilestoneCursor;
import com.kopite.devspace.milestone.application.port.MilestoneCursorCodec;
import com.kopite.devspace.milestone.application.query.MilestoneListFilter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import com.kopite.devspace.milestone.domain.MilestoneValues;
import java.util.Base64;
import java.util.UUID;

@Component
public class SignedMilestoneCursorCodec implements MilestoneCursorCodec {
    private final byte[] signingKey;

    public SignedMilestoneCursorCodec(@Value("${app.project.cursor-signing-key}") String signingKey) {
        this.signingKey = signingKey.getBytes(StandardCharsets.UTF_8);
        if (this.signingKey.length < 32) throw new IllegalArgumentException("Milestone cursor signing key must be at least 32 UTF-8 bytes");
    }

    @Override public String encode(UUID workspace, MilestoneListFilter filter, MilestoneCursor position) {
        String payload = "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(
                ((position.completed()?"1":"0") + "/" + (position.dueDate()==null?"null":position.dueDate()) + "/" + position.id()).getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(workspace, filter, payload));
    }

    @Override public MilestoneCursor decode(UUID workspace, MilestoneListFilter filter, String cursor) {
        try {
            if (cursor.length() > 512) throw new InvalidMilestoneCursorException();
            String[] parts = cursor.split("\\.", -1);
            if (parts.length != 3 || !parts[0].equals("v1")) throw new InvalidMilestoneCursorException();
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(workspace, filter, parts[0] + "." + parts[1]), supplied)) throw new InvalidMilestoneCursorException();
            String[] position = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8).split("/", -1);
            if (position.length != 3) throw new InvalidMilestoneCursorException();
            if(!position[0].equals("0") && !position[0].equals("1")) throw new InvalidMilestoneCursorException();
            var decoded = new MilestoneCursor(position[0].equals("1"),position[1].equals("null")?null:MilestoneValues.date(position[1],"cursor"),UUID.fromString(position[2]));
            if (!encode(workspace, filter, decoded).equals(cursor)) throw new InvalidMilestoneCursorException();
            return decoded;
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            throw new InvalidMilestoneCursorException();
        }
    }

    private byte[] sign(UUID workspace, MilestoneListFilter filter, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            // All context fields have fixed UUID, enum or integer syntax.
            String bound = "milestones:"+workspace+":"+filter.scope()+":"+filter.projectId()+":"+filter.projectStatus()+":"+filter.status()+":"+filter.limit()
                +":completed-asc,dueDate-asc-nulls-last,id-asc:"+payload;
            return mac.doFinal(bound.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException impossible) { throw new IllegalStateException("Cannot sign milestone cursor", impossible); }
    }
}
