package com.kopite.devspace.projectcategory.application;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
final class CategoryRequestHash {
    private CategoryRequestHash() {}
    static String of(String rawName) {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeInt(1);byte[] value=rawName.getBytes(StandardCharsets.UTF_8);
            out.writeInt(value.length);out.write(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch(IOException|NoSuchAlgorithmException ex) { throw new IllegalStateException("Cannot fingerprint Category",ex); }
    }
}
