package com.kopite.devspace.journal.application.model;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
public record JournalCursor(LocalDate entryDate,Instant createdAt,UUID id) {}
