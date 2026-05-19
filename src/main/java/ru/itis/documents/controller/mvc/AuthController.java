package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.dto.RegisterForm;
import ru.itis.documents.integration.google.GoogleOidcClient;
import ru.itis.documents.integration.google.GoogleOidcProperties;
import ru.itis.documents.security.ManualAuthenticationService;
import ru.itis.documents.service.AuthService;

@Controller
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private final AuthService authService;
    private final GoogleOidcClient googleOidcClient;
    private final GoogleOidcProperties googleOidcProperties;
    private final ManualAuthenticationService manualAuthenticationService;

    public AuthController(AuthService authService,
                          GoogleOidcClient googleOidcClient,
                          GoogleOidcProperties googleOidcProperties,
                          ManualAuthenticationService manualAuthenticationService) {
        this.authService = authService;
        this.googleOidcClient = googleOidcClient;
        this.googleOidcProperties = googleOidcProperties;
        this.manualAuthenticationService = manualAuthenticationService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/oauth/google")
    public String googleLogin(jakarta.servlet.http.HttpSession session,
                              RedirectAttributes redirectAttributes) {
        if (!googleOidcProperties.isEnabled()) {
            redirectAttributes.addFlashAttribute("oauthError", "Вход через Google сейчас отключён");
            return "redirect:/auth/login";
        }

        try {
            return "redirect:" + googleOidcClient.buildAuthorizationUrl(session);
        } catch (Exception ex) {
            log.error("Failed to start Google OAuth login", ex);
            redirectAttributes.addFlashAttribute("oauthError", "Не удалось начать вход через Google");
            return "redirect:/auth/login";
        }
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new RegisterForm());
        }
        return "auth/register";
    }

    @GetMapping("/oauth/google/callback")
    public String googleCallback(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String code,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String state,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String error,
            jakarta.servlet.http.HttpServletRequest request,
            RedirectAttributes redirectAttributes
    ) {
        if (!googleOidcProperties.isEnabled()) {
            redirectAttributes.addFlashAttribute("oauthError", "Вход через Google сейчас отключён");
            return "redirect:/auth/login";
        }

        if (error != null) {
            redirectAttributes.addFlashAttribute("oauthError", toUserFriendlyGoogleProviderError(error));
            return "redirect:/auth/login";
        }

        if (code == null || code.isBlank()) {
            redirectAttributes.addFlashAttribute("oauthError", "Google не подтвердил вход. Попробуйте ещё раз.");
            return "redirect:/auth/login";
        }

        try {
            var userInfo = googleOidcClient.fetchUserInfoByCode(code, state, request.getSession(true));

            var user = authService.loginOrRegisterWithGoogle(
                    userInfo.sub(),
                    userInfo.email(),
                    userInfo.emailVerified()
            );

            manualAuthenticationService.login(user, request);
            return "redirect:/app";
        } catch (Exception ex) {
            log.error("Google OAuth callback failed: codePresent={}, statePresent={}",
                    code != null && !code.isBlank(),
                    state != null && !state.isBlank(),
                    ex);
            redirectAttributes.addFlashAttribute("oauthError", toUserFriendlyGoogleError(ex));
            return "redirect:/auth/login";
        }
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("form") RegisterForm form,
            BindingResult bindingResult
    ) {
        if (bindingResult.hasErrors()) {
            form.setPassword(null);
            form.setConfirmPassword(null);
            return "auth/register";
        }

        try {
            authService.register(form);
        } catch (AuthService.EmailAlreadyUsedException ex) {
            log.warn("Registration failed: email already used, email={}",
                    form.getEmail(),
                    ex);
            bindingResult.addError(new FieldError("form", "email", ex.getMessage()));
            form.setPassword(null);
            form.setConfirmPassword(null);
            return "auth/register";
        }

        return "redirect:/auth/login?registered";
    }

    private String toUserFriendlyGoogleProviderError(String error) {
        if (error == null || error.isBlank()) {
            return "Не удалось выполнить вход через Google. Попробуйте ещё раз.";
        }

        return switch (error) {
            case "access_denied" -> "Вход через Google был отменён.";
            case "invalid_request" -> "Не удалось начать вход через Google. Попробуйте ещё раз.";
            case "unauthorized_client" -> "Вход через Google сейчас недоступен. Попробуйте позже.";
            case "temporarily_unavailable" -> "Сервис Google временно недоступен. Попробуйте позже.";
            case "server_error" -> "На стороне Google произошла ошибка. Попробуйте позже.";
            default -> "Не удалось выполнить вход через Google. Попробуйте ещё раз.";
        };
    }

    private String toUserFriendlyGoogleError(Exception ex) {
        String message = ex.getMessage();

        if (message == null || message.isBlank()) {
            return "Не удалось выполнить вход через Google. Попробуйте ещё раз.";
        }

        if (message.contains("Google OAuth отключен")
                || message.contains("GOOGLE_OAUTH_CLIENT_ID")
                || message.contains("GOOGLE_OAUTH_CLIENT_SECRET")
                || message.contains("GOOGLE_OAUTH_REDIRECT_URI")
                || message.contains("GOOGLE_OAUTH_DISCOVERY_URI")
                || message.contains("GOOGLE_OAUTH_SCOPE")) {
            return "Вход через Google сейчас недоступен. Попробуйте позже.";
        }

        if (message.contains("discovery document")) {
            return "Не удалось связаться с Google. Попробуйте позже.";
        }

        if (message.contains("Authorization code отсутствует")) {
            return "Google не подтвердил вход. Попробуйте ещё раз.";
        }

        if (message.contains("Google не вернул state")
                || message.contains("OAuth state не совпадает")
                || message.contains("В сессии отсутствует OAuth state")
                || message.contains("В сессии отсутствует code_verifier")) {
            return "Сеанс входа через Google устарел или был прерван. Попробуйте ещё раз.";
        }

        if (message.contains("token")) {
            return "Не удалось подтвердить вход через Google. Попробуйте ещё раз.";
        }

        if (message.contains("userinfo")) {
            return "Не удалось получить данные аккаунта Google. Попробуйте позже.";
        }

        if (message.contains("Google не вернул email")
                || message.contains("email отсутствует")
                || message.contains("email is missing")) {
            return "Google не передал email аккаунта. Попробуйте другой аккаунт.";
        }

        return "Не удалось выполнить вход через Google. Попробуйте ещё раз.";
    }
}


//AuthController — это MVC-контроллер для авторизации. Он показывает страницу логина и регистрации,
// обрабатывает регистрацию через RegisterForm, @Valid и BindingResult, а создание пользователя передаёт в AuthService.
// При ошибках формы пароль очищается, чтобы не возвращать его в HTML.
// Также контроллер запускает Google OAuth: через GoogleOidcClient строит authorization URL,
// принимает callback с code, state и error, получает данные Google-аккаунта, передаёт их в AuthService.loginOrRegisterWithGoogle,
// а потом вручную авторизует пользователя через ManualAuthenticationService. Ошибки Google OAuth переводятся в
// понятные сообщения и передаются на страницу логина через flash attributes.
//показать форму
//принять форму
//показать ошибки
//запустить Google OAuth
//принять callback
//передать данные в сервис
//после OAuth вручную авторизовать пользователя