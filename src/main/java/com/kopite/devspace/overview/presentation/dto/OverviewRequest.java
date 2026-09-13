package com.kopite.devspace.overview.presentation.dto;
import com.kopite.devspace.overview.application.*;
import java.util.UUID;
public record OverviewRequest(String scope,String projectId) {
    public OverviewFilter filter() {
        UUID id=null;
        if(projectId!=null) {
            try{id=UUID.fromString(projectId);if(!id.toString().equalsIgnoreCase(projectId))throw new IllegalArgumentException();}
            catch(IllegalArgumentException ex){throw new OverviewValidationException("projectId");}
        }
        return new OverviewFilter(scope==null?"all":scope,id);
    }
}
