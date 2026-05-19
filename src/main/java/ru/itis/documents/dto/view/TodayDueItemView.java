package ru.itis.documents.dto.view;

import ru.itis.documents.domain.enums.CareActionType;

import java.time.LocalDate;

public record TodayDueItemView(
        Long taskId,
        Long plantId,
        String plantNickname,
        String speciesName,
        CareActionType type,
        String typeLabel,
        LocalDate dueDate,
        long overdueDays
) {
}