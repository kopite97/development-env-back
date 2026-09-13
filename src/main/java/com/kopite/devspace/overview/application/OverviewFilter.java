package com.kopite.devspace.overview.application;
import java.util.Set;
import java.util.UUID;
public record OverviewFilter(String scope,UUID projectId) {
    public OverviewFilter {
        if(scope==null || !Set.of("all","unity","server").contains(scope)) throw new OverviewValidationException("scope");
    }
}
