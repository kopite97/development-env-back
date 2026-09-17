package com.kopite.devspace.widget.application;
import java.util.UUID;
public interface WidgetCursorCodec {
    String encode(UUID workspace,String context,String position);
    String decode(UUID workspace,String context,String cursor);
}
