package com.kopite.devspace.link.application.command;
import com.kopite.devspace.link.domain.LinkValues;
public record UpdateLinkCommand(long revision,String label,String description,String url,String scope) {
    public LinkValues applyTo(LinkValues old) {
        return new LinkValues(label==null?old.label():label,description==null?old.description():description,
            url==null?old.url():url,scope==null?old.scope():scope);
    }
}
