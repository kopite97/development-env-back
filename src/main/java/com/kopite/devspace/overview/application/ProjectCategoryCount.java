package com.kopite.devspace.overview.application;

import java.util.UUID;

public record ProjectCategoryCount(UUID categoryId, long active, long archived) {}
