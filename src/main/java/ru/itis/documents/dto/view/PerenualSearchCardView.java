package ru.itis.documents.dto.view;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PerenualSearchCardView(
        String query,
        long perenualId,
        String name,
        String scientificName,
        List<String> scientificNames,
        String description,
        String cycle,
        String watering,
        Integer wateringMinDays,
        Integer wateringMaxDays,
        String careLevel,
        List<String> sunlight,
        String imageUrl,
        boolean alreadyImported,
        Long localSpeciesId
) {
}