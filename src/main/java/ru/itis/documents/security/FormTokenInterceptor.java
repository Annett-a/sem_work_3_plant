package ru.itis.documents.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import ru.itis.documents.exception.DuplicateFormSubmissionException;

@Component
public class FormTokenInterceptor implements HandlerInterceptor {

    private final FormTokenService formTokenService;

    public FormTokenInterceptor(FormTokenService formTokenService) {
        this.formTokenService = formTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (!requiresFormTokenCheck(request)) {
            return true;
        }

        HttpSession session = request.getSession(false);
        String token = request.getParameter(FormTokenService.PARAMETER_NAME);

        if (!formTokenService.consumeToken(session, token)) {
            throw new DuplicateFormSubmissionException();
        }

        return true;
    }

    private boolean requiresFormTokenCheck(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod());
    }
}