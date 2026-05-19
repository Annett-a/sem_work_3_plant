package ru.itis.documents.dto.view;

import java.time.OffsetDateTime;

public record StaleWateringPlantRawView(
        Long plantId,
        String nickname,
        String speciesName,
        OffsetDateTime lastWaterTime
) {
}