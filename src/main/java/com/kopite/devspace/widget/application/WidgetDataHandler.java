package com.kopite.devspace.widget.application;
import java.util.UUID;
public interface WidgetDataHandler {
    String type();
    boolean paginated();
    Result read(UUID user,WidgetConfig config,String cursor);
    record Result(WidgetPayload data,WidgetDataEnvelope.Page page,boolean empty,WidgetDataEnvelope.Problem problem) {
        public static Result unavailable(String code){return new Result(null,null,false,new WidgetDataEnvelope.Problem(code,false));}
    }
}
