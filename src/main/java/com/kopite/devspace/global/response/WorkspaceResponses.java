package com.kopite.devspace.global.response;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

/** The application supplies a counter observed in the body transaction, never a later lookup. */
public final class WorkspaceResponses {
    public static final String REVISION_HEADER = "X-Workspace-Data-Revision";
    private WorkspaceResponses() {}
    public static <T> ResponseEntity<T> ok(T body, Long revision) { return response(200,body,revision); }
    public static <T> ResponseEntity<T> created(T body, Long revision) { return response(201,body,revision); }
    private static <T> ResponseEntity<T> response(int status,T body,Long revision) {
        var builder=ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if(revision!=null) builder.header(REVISION_HEADER,Long.toString(revision));
        return builder.body(body);
    }
}
