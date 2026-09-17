package com.kopite.devspace.widget.presentation;
import com.kopite.devspace.widget.application.WidgetCreationResult;
import com.kopite.devspace.widget.domain.WidgetException;
import com.kopite.devspace.global.response.WorkspaceResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import java.util.Set;
public final class WidgetHttp {
    private WidgetHttp(){}
    public static void query(HttpServletRequest r,String... allowed){
        var variables=r.getAttribute(org.springframework.web.servlet.HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if(variables instanceof java.util.Map<?,?> path&&path.get("id") instanceof String id)com.kopite.devspace.widget.application.WidgetJson.uuid(id,"id");
        var names=Set.of(allowed);r.getParameterMap().forEach((k,v)->{if(!names.contains(k)||v.length!=1||v[0].isEmpty())throw WidgetException.invalid(k);});
    }
    public static long integer(String v,String field,long min,long max){try{if(v==null||!v.matches("[0-9]+"))throw WidgetException.invalid(field);long n=Long.parseLong(v);if(n<min||n>max)throw WidgetException.invalid(field);return n;}catch(NumberFormatException e){throw WidgetException.invalid(field);}}
    public static ResponseEntity<String> created(WidgetCreationResult r){var b=ResponseEntity.status(201).contentType(MediaType.APPLICATION_JSON).cacheControl(CacheControl.noStore()).header(HttpHeaders.LOCATION,r.location());if(r.dataRevision()!=null)b.header(WorkspaceResponses.REVISION_HEADER,r.dataRevision().toString());return b.body(r.body());}
}
