package com.kopite.devspace.project.application.model;

import java.time.Instant;
import java.util.UUID;

public record ProjectCursor(Instant createdAt, UUID id) {}
