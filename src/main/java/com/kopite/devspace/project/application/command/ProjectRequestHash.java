package com.kopite.devspace.project.application.command;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ProjectRequestHash {
    private ProjectRequestHash() {}

    static String of(CreateProjectCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(3);
            // Fixed field order and length prefixes preserve values and omission without JSON formatting noise.
            for (Object value : new Object[]{command.name(), command.subtitle(), command.stack(),
                    command.progress(), command.currentMilestone(), command.repositoryUrl()}) {
                if (value == null) {
                    output.writeInt(-1);
                } else {
                    byte[] field = value.toString().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(field.length);
                    output.write(field);
                }
            }
            output.writeBoolean(command.category().present());
            {
                String value=command.category().rawValue();
                if(value==null) output.writeInt(-1);
                else {
                    byte[] field=value.getBytes(StandardCharsets.UTF_8);
                    output.writeInt(field.length);
                    output.write(field);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint project request", impossible);
        }
    }
}
