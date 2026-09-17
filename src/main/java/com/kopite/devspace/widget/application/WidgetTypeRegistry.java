package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.WidgetException;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class WidgetTypeRegistry {
    private final Map<String,WidgetTypeDefinition> types;
    public WidgetTypeRegistry(List<WidgetTypeDefinition> definitions) {
        var map=new TreeMap<String,WidgetTypeDefinition>();
        for(var d:definitions) {
            if(d.type()==null||!d.type().matches("[a-z][a-z0-9.-]*")||d.configVersion()<1||d.sizes().isEmpty()||!Set.of("small","medium","wide").containsAll(d.sizes())||!Set.of("ready","unavailable").contains(d.availability())||!WidgetJson.parse(d.configSchema()).isObject()||map.putIfAbsent(d.type(),d)!=null)
                throw new IllegalStateException("Invalid or duplicate Widget definition");
        }
        types=Collections.unmodifiableMap(map);
    }
    public WidgetTypeDefinition get(String type,int version) {
        var d=types.get(type);if(d==null)throw WidgetException.invalid("type");if(version!=d.configVersion())throw WidgetException.invalid("configVersion");return d;
    }
    public List<WidgetTypeDefinition> all(){return List.copyOf(types.values());}
}
