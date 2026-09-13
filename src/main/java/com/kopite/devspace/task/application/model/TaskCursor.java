package com.kopite.devspace.task.application.model;
import java.time.Instant;
import java.util.UUID;
public record TaskCursor(Instant createdAt, UUID id) {}
