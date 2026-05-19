package ru.itis.documents.form;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Getter
@Setter
public class UserPlantUpdateForm {

    @NotNull(message = "Выберите вид растения")
    private Long speciesId;

    @Positive(message = "Некорректная комната")
    private Long roomId;

    @NotBlank(message = "Укажи название (кличку) растения")
    @Size(max = 120, message = "Название слишком длинное (макс 120)")
    private String nickname;

    @PastOrPresent(message = "Дата покупки не может быть в будущем")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate purchaseDate;

    @Size(max = 4000, message = "Заметка слишком длинная")
    private String notes;
}