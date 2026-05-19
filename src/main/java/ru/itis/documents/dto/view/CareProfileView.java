package ru.itis.documents.dto.view;

public record CareProfileView(
        Integer waterIntervalDays,
        String lightLevel,
        Integer humidityPercent,
        String notes,
        String wateringText
) {
}