package com.kopite.devspace.widget.application;
import com.kopite.devspace.widget.domain.WidgetException;
import tools.jackson.core.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.*;
import java.util.*;

/** Strict JSON at Widget boundaries; independent of the application's permissive legacy parsers. */
public final class WidgetJson {
    public static final JsonMapper MAPPER=JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private WidgetJson(){}
    public static JsonNode parse(String value) {
        try {var n=MAPPER.readTree(value);if(n==null)throw WidgetException.invalid("body");return n;}
        catch(JacksonException ex){throw WidgetException.invalid("body");}
    }
    public static void fields(JsonNode n,String... allowed) {
        if(!n.isObject())throw WidgetException.invalid("body");var keys=Set.of(allowed);
        for(var entry:n.properties())if(!keys.contains(entry.getKey())||entry.getValue().isNull())throw WidgetException.invalid(entry.getKey());
    }
    public static String text(JsonNode n,String key) {
        var v=n.get(key);if(v==null||!v.isString())throw WidgetException.invalid(key);return v.asString();
    }
    public static long integer(JsonNode n,String key,long min,long max) {
        var v=n.get(key);if(v==null||!v.isIntegralNumber()||!v.canConvertToLong()||v.longValue()<min||v.longValue()>max)throw WidgetException.invalid(key);
        return v.longValue();
    }
    public static UUID uuid(String value,String field) {
        try {var id=UUID.fromString(value);if(!id.toString().equalsIgnoreCase(value))throw WidgetException.invalid(field);return id;}
        catch(IllegalArgumentException ex){throw WidgetException.invalid(field);}
    }
    public static String canonical(JsonNode n){return MAPPER.writeValueAsString(sorted(n));}
    private static JsonNode sorted(JsonNode n) {
        if(n.isObject()){ObjectNode o=MAPPER.createObjectNode();var keys=new TreeSet<String>();n.properties().forEach(e->keys.add(e.getKey()));keys.forEach(k->o.set(k,sorted(n.get(k))));return o;}
        if(n.isArray()){ArrayNode a=MAPPER.createArrayNode();n.forEach(v->a.add(sorted(v)));return a;}return n;
    }
}
