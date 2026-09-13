package com.kopite.devspace.dashboard.domain;
import java.util.Optional;
import java.util.UUID;
public interface HomeDashboardRepository {
    Optional<HomeDashboard> find(UUID workspace);
    Optional<HomeDashboard> lock(UUID workspace);
    void insert(HomeDashboard dashboard);
    void flush();
}
