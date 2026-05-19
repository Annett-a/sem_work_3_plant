package ru.itis.documents.dto.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class UserPlantCardView {
    Long id;
    String nickname;
    String speciesName;
    String roomName;
    LocalDate purchaseDate;
    String nextWateringText;
    CapriciousnessView cap;
}