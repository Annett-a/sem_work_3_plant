package ru.itis.documents.dto.view;

import java.util.List;

public record CapriciousnessView(
        String key,
        String label,
        String cssClass,
        Integer score,
        List<String> reasons
) {
}