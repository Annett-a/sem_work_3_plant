package ru.itis.documents.dto.view;

public record TagFilterOptionView(
        Long id,
        String label,
        boolean selected
) {
}