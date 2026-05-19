package ru.itis.documents.dto.view;

import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.domain.enums.CareTaskStatus;

import java.time.LocalDate;

public record CareTaskItemView(
        Long id,
        CareActionType type,
        String typeLabel,
        CareTaskStatus status,
        LocalDate dueDate
) {
}