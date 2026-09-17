package com.kopite.devspace.widget.application;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.HexFormat;
public final class WidgetRequestHash {
    private WidgetRequestHash(){}
    public static String of(String path,String body) {
        try {var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);out.writeInt(1);
            for(String text:new String[]{path,WidgetJson.canonical(WidgetJson.parse(body))}){var b=text.getBytes(StandardCharsets.UTF_8);out.writeInt(b.length);out.write(b);}
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        }catch(IOException|NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
