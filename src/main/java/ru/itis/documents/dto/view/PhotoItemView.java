package ru.itis.documents.dto.view;

import lombok.Value;

import java.time.OffsetDateTime;

@Value
public class PhotoItemView {
    Long id;
    String originalName;
    OffsetDateTime uploadedAt;
}