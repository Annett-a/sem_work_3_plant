package ru.itis.documents.dto.view;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SelectOptionView {
    Long id;
    String label;
}