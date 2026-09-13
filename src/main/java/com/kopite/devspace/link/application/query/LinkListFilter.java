package com.kopite.devspace.link.application.query;
import com.kopite.devspace.link.domain.LinkValidationException;
public record LinkListFilter(String scope,String query) {
    public LinkListFilter {
        if(!"all".equals(scope)&&!"unity".equals(scope)&&!"server".equals(scope)) throw new LinkValidationException("scope","must be all, unity or server");
        if(query==null) query="";
    }
}
