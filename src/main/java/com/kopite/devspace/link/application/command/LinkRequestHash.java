package com.kopite.devspace.link.application.command;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
final class LinkRequestHash {
    private LinkRequestHash() {}
    static String of(CreateLinkCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(1);
            for (Object value : new Object[]{command.label(), command.description(), command.url(), command.scope()}) {
                if (value == null) output.writeInt(-1);
                else {
                    byte[] field = value.toString().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(field.length);
                    output.write(field);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint link request", impossible);
        }
    }
}
