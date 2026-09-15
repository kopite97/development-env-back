package com.kopite.devspace.compatibility.application;
import java.util.UUID;
public record LegacyCreateRequest(CreationResource resource,String hash,UUID projectId) {}
