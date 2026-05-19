package ru.itis.documents.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.AppUser;

@Service
public class ManualAuthenticationService {

    public void login(AppUser user, HttpServletRequest request) {
        if (!user.isEnabled()) {
            throw new DisabledException("Пользователь отключен");
        }

        request.changeSessionId();

        AppUserPrincipal principal = new AppUserPrincipal(user);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                principal.getAuthorities()
        );

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        request.getSession(true).setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context
        );
    }
}

/*
1. AuthController получил успешный ответ от Google
2. AuthService нашёл или создал AppUser
3. AuthController вызвал manualAuthenticationService.login(user, request)
4. ManualAuthenticationService проверил user.isEnabled()
5. Сменил session id для защиты от session fixation
6. Создал AppUserPrincipal из AppUser
7. Создал authenticated Authentication с principal и roles
8. Создал пустой SecurityContext
9. Положил Authentication в SecurityContext
10. Установил SecurityContext в SecurityContextHolder
11. Сохранил SecurityContext в HTTP-сессию
12. Пользователь стал авторизованным
13. Controller делает redirect:/app
 */
/*
ManualAuthenticationService нужен для ручной авторизации пользователя после Google OAuth.
Сначала он проверяет, что пользователь включён, затем меняет id сессии через changeSessionId,
чтобы защититься от session fixation. После этого создаётся AppUserPrincipal,
из него создаётся authenticated UsernamePasswordAuthenticationToken с ролями пользователя.
Этот authentication кладётся в новый SecurityContext, context устанавливается в SecurityContextHolder
для текущего запроса и дополнительно сохраняется в HTTP-сессию под стандартным ключом Spring Security,
чтобы пользователь остался авторизованным после redirect.
 */