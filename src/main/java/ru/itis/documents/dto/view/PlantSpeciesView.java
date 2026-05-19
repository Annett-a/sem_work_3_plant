package ru.itis.documents.dto.view;

import java.util.List;

public record PlantSpeciesView(
        Long id,
        String name,
        String latinName,
        String imageUrl,
        String description,
        CareProfileView care,
        CapriciousnessView capriciousness,
        List<String> tags,
        List<Long> tagIds
) {
}
