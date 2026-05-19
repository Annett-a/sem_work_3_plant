package ru.itis.documents.integration.perenual;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record PerenualSpeciesDetails(
        long id,
        String commonName,
        List<String> scientificNames,
        String description,
        String cycle,
        String watering,
        PerenualWateringBenchmark wateringBenchmark,
        List<String> sunlight,
        String careLevel,
        String imageUrl,
        JsonNode rawNode
) {
}