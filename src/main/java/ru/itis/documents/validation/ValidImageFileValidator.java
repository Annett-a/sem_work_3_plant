package ru.itis.documents.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;

public class ValidImageFileValidator implements ConstraintValidator<ValidImageFile, MultipartFile> {

    private static final long MAX_BYTES = 10L * 1024L * 1024L;

    @Override
    public boolean isValid(MultipartFile value, ConstraintValidatorContext context) {
        if (value == null || value.isEmpty()) {
            return fail(context, "Выберите файл (JPEG/PNG/WebP)");
        }

        if (value.getSize() > MAX_BYTES) {
            return fail(context, "Файл слишком большой (до 10 МБ)");
        }

        String contentType = value.getContentType();
        if (contentType == null) {
            return fail(context, "Нужен файл-изображение (JPEG/PNG/WebP)");
        }

        String ct = contentType.toLowerCase(Locale.ROOT);
        boolean ok = ct.equals("image/jpeg")
                || ct.equals("image/jpg")
                || ct.equals("image/pjpeg")
                || ct.equals("image/png")
                || ct.equals("image/webp");

        if (!ok) {
            return fail(context, "Допустимы только JPEG/PNG/WebP");
        }

        return true;
    }

    private static boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}