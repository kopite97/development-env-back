package com.kopite.devspace.global.response;

import java.util.Map;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(requiredProperties = {"code", "message", "fieldErrors", "requestId"})
public record ApiError(String code, String message, Map<String, String> fieldErrors, String requestId) {
    public static ApiError of(String code, String message, Map<String, String> fields) {
        return new ApiError(code, message, fields, UUID.randomUUID().toString());
    }
}
