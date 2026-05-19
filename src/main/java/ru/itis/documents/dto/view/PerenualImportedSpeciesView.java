package ru.itis.documents.dto.view;

public record PerenualImportedSpeciesView(
        long perenualId,
        long localSpeciesId,
        String name,
        String latinName
) {
}