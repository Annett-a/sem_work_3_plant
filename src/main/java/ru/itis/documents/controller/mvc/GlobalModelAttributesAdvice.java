package ru.itis.documents.controller.mvc;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import ru.itis.documents.integration.google.GoogleOidcProperties;
import ru.itis.documents.security.FormTokenService;

@ControllerAdvice(basePackages = "ru.itis.documents.controller.mvc")
public class GlobalModelAttributesAdvice {

    private final GoogleOidcProperties googleOidcProperties;
    private final FormTokenService formTokenService;

    public GlobalModelAttributesAdvice(GoogleOidcProperties googleOidcProperties,
                                       FormTokenService formTokenService) {
        this.googleOidcProperties = googleOidcProperties;
        this.formTokenService = formTokenService;
    }

    @ModelAttribute("googleOAuthEnabled")
    public boolean googleOAuthEnabled() {
        return googleOidcProperties.isEnabled();
    }

    @ModelAttribute("googleOAuthReady")
    public boolean googleOAuthReady() {
        return googleOidcProperties.isEnabled()
                && hasText(googleOidcProperties.getClientId())
                && hasText(googleOidcProperties.getClientSecret())
                && hasText(googleOidcProperties.getRedirectUri());
    }

    @ModelAttribute("formTokenParam")
    public String formTokenParam() {
        return FormTokenService.PARAMETER_NAME;
    }

    @ModelAttribute("formToken")
    public String formToken(HttpServletRequest request) {
        return formTokenService.issueToken(request.getSession());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}