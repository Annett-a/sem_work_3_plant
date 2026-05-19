package ru.itis.documents.form;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PlantCandidateSelectForm {

    @NotBlank(message = "Выбери кандидата")
    @Size(max = 255, message = "Слишком длинное научное название")
    private String selectedScientificName;
}