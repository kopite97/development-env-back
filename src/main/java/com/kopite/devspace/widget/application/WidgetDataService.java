package com.kopite.devspace.widget.application;
import com.kopite.devspace.auth.application.CurrentUserService;
import com.kopite.devspace.widget.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.Clock;
import java.util.*;
@Service @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class WidgetDataService {
    private final CurrentUserService users;private final WidgetQueryService queries;private final WidgetCursorCodec cursors;private final Clock clock;
    private final Map<String,WidgetDataHandler> handlers;
    public WidgetDataService(CurrentUserService users,WidgetQueryService queries,WidgetCursorCodec cursors,Clock projectClock,WidgetTypeRegistry types,List<WidgetDataHandler> definitions) {
        this.users=users;this.queries=queries;this.cursors=cursors;this.clock=projectClock;var map=new HashMap<String,WidgetDataHandler>();
        for(var d:definitions)if(map.putIfAbsent(d.type(),d)!=null)throw new IllegalStateException("Duplicate Widget data handler");
        if(!map.keySet().equals(new HashSet<>(types.all().stream().map(WidgetTypeDefinition::type).toList())))throw new IllegalStateException("Every Widget type needs exactly one data handler");handlers=Map.copyOf(map);
    }
    public WidgetDataEnvelope get(UUID user,UUID id,String cursor) {
        var workspace=users.resolve(user).workspace();var s=queries.get(user,id);var handler=handlers.get(s.type());
        if(cursor!=null&&!handler.paginated())throw WidgetException.invalid("cursor");
        String context="data:"+id+":"+s.revision()+":"+s.type()+":"+WidgetJson.MAPPER.writeValueAsString(s.config());
        String businessCursor=cursor==null?null:cursors.decode(workspace.getId(),context,cursor);
        if(s.referenceState().equals("missingCategory"))return unavailable(s,"REFERENCE_MISSING");
        var result=handler.read(user,s.config(),businessCursor);
        if(result.data()==null){if(result.problem()==null)throw new IllegalStateException("Handler returned no data or problem");return unavailable(s,result.problem().code());}
        var page=result.page();if(page!=null&&page.nextCursor()!=null)page=new WidgetDataEnvelope.Page(page.total(),cursors.encode(workspace.getId(),context,page.nextCursor()));
        return new WidgetDataEnvelope(s.id(),s.type(),s.revision(),s.configVersion(),1,result.empty()?"empty":"ready","current",clock.instant(),null,null,result.data(),page,result.problem(),s.dataRevision());
    }
    private WidgetDataEnvelope unavailable(WidgetSnapshot s,String code){return new WidgetDataEnvelope(s.id(),s.type(),s.revision(),s.configVersion(),1,"unavailable","unknown",clock.instant(),null,null,null,null,new WidgetDataEnvelope.Problem(code,false),s.dataRevision());}
}
