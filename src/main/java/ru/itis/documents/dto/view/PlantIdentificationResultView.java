package ru.itis.documents.dto.view;

import java.util.List;

public record PlantIdentificationResultView(
        String bestScientificName,
        Double bestScore,
        List<PlantIdentificationCandidateView> topCandidates
) {
}