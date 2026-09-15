package com.kopite.devspace.link.application.model;
public record LinkMutation(LinkSnapshot item,long collectionRevision,@com.fasterxml.jackson.annotation.JsonIgnore Long dataRevision) {
    public LinkMutation(LinkSnapshot item,long collectionRevision){this(item,collectionRevision,null);}
    public LinkMutation observed(long workspaceRevision){return new LinkMutation(item,collectionRevision,workspaceRevision);}
}
