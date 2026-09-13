package com.kopite.devspace.milestone.application.model;
import java.time.LocalDate;
import java.util.UUID;
public record MilestoneCursor(boolean completed,LocalDate dueDate,UUID id) {}
