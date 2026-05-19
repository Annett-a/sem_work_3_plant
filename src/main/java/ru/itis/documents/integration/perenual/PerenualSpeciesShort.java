package ru.itis.documents.integration.perenual;

import java.util.List;

public record PerenualSpeciesShort(
        long id,
        String commonName,
        List<String> scientificNames,
        String imageUrl
) {
}