package ru.itis.documents.dto.view;

public record PlantIdentificationCandidateView(
        String scientificName,
        double score
) {
}