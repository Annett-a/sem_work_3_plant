package ru.itis.documents.dto.view;

import java.time.OffsetDateTime;

public record StaleWateringPlantView(
        Long plantId,
        String nickname,
        String speciesName,
        OffsetDateTime lastWaterTime,
        long daysWithoutWatering
) {
}