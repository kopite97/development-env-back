package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.WidgetSelection;
import java.util.Set;
public interface WidgetTypeDefinition {
    String type();
    int configVersion();
    Set<String> sizes();
    String configSchema();
    WidgetConfig decode(String json);
    default WidgetSelection selection(WidgetConfig config){return null;}
    default String availability(){return "ready";}
}
