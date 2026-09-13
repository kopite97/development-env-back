package com.kopite.devspace.overview.application;
import java.util.List;
import java.util.UUID;
public interface OverviewProjectRepository {
    record Count(String scope,String status,long count) {}
    List<Count> counts(UUID workspace,OverviewFilter filter);
}
