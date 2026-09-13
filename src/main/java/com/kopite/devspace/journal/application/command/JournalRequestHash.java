package com.kopite.devspace.journal.application.command;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
final class JournalRequestHash {
    private JournalRequestHash() {}
    static String of(CreateJournalCommand command) {
        try {
            var bytes = new ByteArrayOutputStream();
            var output = new DataOutputStream(bytes);
            output.writeInt(1);
            for (Object value : new Object[]{command.title(), command.projectId(), command.body(), command.entryDate()}) {
                if (value == null) output.writeInt(-1);
                else {
                    byte[] field = value.toString().getBytes(StandardCharsets.UTF_8);
                    output.writeInt(field.length);
                    output.write(field);
                }
            }
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Cannot fingerprint journal request", impossible);
        }
    }
}
