package ru.itis.documents.form;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.validation.ValidImageFile;

@Getter
@Setter
public class PlantIdentificationForm {

    @ValidImageFile
    private MultipartFile photo;
}