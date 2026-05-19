package ru.itis.documents.dto.view;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.List;

@Value
@Builder
public class UserPlantDetailsView {
    Long id;

    String nickname;

    Long speciesId;
    String speciesName;
    String speciesLatinName;

    String displayName;
    String displaySpeciesLatinName;

    Long roomId;
    String roomName;

    LocalDate purchaseDate;
    String notes;

    CapriciousnessView cap;
    Integer waterIntervalDays;
    String lightLevel;
    String nextWateringText;

    List<CareTaskItemView> tasks;
}