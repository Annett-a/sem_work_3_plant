package ru.itis.documents.dto;

public record ApiErrorResponse(
        String code,
        String message,
        Object details
) {
}