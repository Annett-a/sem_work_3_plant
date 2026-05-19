package ru.itis.documents.form;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CareEventCreateForm {

    @Size(max = 1000, message = "Комментарий слишком длинный (макс 1000)")
    private String comment;
}