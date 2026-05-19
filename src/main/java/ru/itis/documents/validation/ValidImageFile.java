package ru.itis.documents.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Constraint(validatedBy = ValidImageFileValidator.class)
@Target({FIELD, PARAMETER})
@Retention(RUNTIME)
public @interface ValidImageFile {

    String message() default "Загрузи фото (JPEG/PNG/WebP), до 10 МБ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}