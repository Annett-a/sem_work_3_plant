package ru.itis.documents.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerenualPreviewView(
        String query,
        long perenualId,
        String name,
        String scientificName,
        List<String> scientificNames,
        String imageUrl,

        boolean alreadyImported,
        Long localSpeciesId
) {
}