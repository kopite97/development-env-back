package com.kopite.devspace.link.application.command;
import com.kopite.devspace.link.domain.LinkValues;
public record UpdateLinkCommand(long revision,String label,String description,String url,LinkProjectSelection project) {
    public UpdateLinkCommand { java.util.Objects.requireNonNull(project); }
    public LinkValues applyTo(LinkValues old) {
        return new LinkValues(label==null?old.label():label,description==null?old.description():description,
            url==null?old.url():url,project.present()?project.id():old.projectId());
    }
}
