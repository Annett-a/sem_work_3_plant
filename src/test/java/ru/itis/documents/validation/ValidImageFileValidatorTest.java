package ru.itis.documents.validation;

import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import ru.itis.documents.validation.ValidImageFileValidator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ValidImageFileValidatorTest {

    private final ValidImageFileValidator validator = new ValidImageFileValidator();

    @Mock
    ConstraintValidatorContext context;

    @Mock
    ConstraintValidatorContext.ConstraintViolationBuilder violationBuilder;

    @BeforeEach
    void setUp() {
        lenient().when(context.buildConstraintViolationWithTemplate(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(violationBuilder);
    }

    @Test
    void isValid_returnsFalseForNullFile() {
        assertThat(validator.isValid(null, context)).isFalse();
        verify(context).disableDefaultConstraintViolation();
    }

    @Test
    void isValid_returnsFalseForEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("photo", new byte[0]);
        assertThat(validator.isValid(file, context)).isFalse();
    }

    @Test
    void isValid_returnsFalseForTooLargeFile() {
        MockMultipartFile file = new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1});
        MockMultipartFile spy = org.mockito.Mockito.spy(file);
        when(spy.getSize()).thenReturn(10L * 1024L * 1024L + 1);

        assertThat(validator.isValid(spy, context)).isFalse();
    }

    @Test
    void isValid_returnsFalseWhenContentTypeMissing() {
        MockMultipartFile file = new MockMultipartFile("photo", "a.jpg", null, new byte[]{1});
        assertThat(validator.isValid(file, context)).isFalse();
    }

    @Test
    void isValid_returnsFalseForUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("photo", "a.gif", "image/gif", new byte[]{1});
        assertThat(validator.isValid(file, context)).isFalse();
    }

    @Test
    void isValid_acceptsSupportedImageTypes() {
        assertThat(validator.isValid(new MockMultipartFile("photo", "a.jpg", "image/jpeg", new byte[]{1}), context)).isTrue();
        assertThat(validator.isValid(new MockMultipartFile("photo", "a.jpg", "image/jpg", new byte[]{1}), context)).isTrue();
        assertThat(validator.isValid(new MockMultipartFile("photo", "a.jpg", "image/pjpeg", new byte[]{1}), context)).isTrue();
        assertThat(validator.isValid(new MockMultipartFile("photo", "a.png", "image/png", new byte[]{1}), context)).isTrue();
        assertThat(validator.isValid(new MockMultipartFile("photo", "a.webp", "image/webp", new byte[]{1}), context)).isTrue();
    }
}
