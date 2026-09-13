package com.kopite.devspace.link.application.command;
import com.kopite.devspace.link.domain.LinkValues;
public record CreateLinkCommand(String label,String description,String url,String scope) {
    public LinkValues values() { return new LinkValues(label,description==null?"":description,url,scope==null?"all":scope); }
}
