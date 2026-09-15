package com.kopite.devspace.link.application.command;
import com.kopite.devspace.link.domain.LinkValues;
public record CreateLinkCommand(String label,String description,String url,LinkProjectSelection project) {
    public CreateLinkCommand { java.util.Objects.requireNonNull(project); }
    public LinkValues values() { return new LinkValues(label,description==null?"":description,url,project.id()); }
}
