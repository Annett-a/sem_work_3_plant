package ru.itis.documents.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.OAuthAccount;
import ru.itis.documents.domain.entity.Role;
import ru.itis.documents.domain.enums.OAuthProvider;
import ru.itis.documents.dto.RegisterForm;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.OAuthAccountRepository;
import ru.itis.documents.repository.RoleRepository;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "ROLE_USER";

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuthAccountRepository oauthAccountRepository;

    public AuthService(AppUserRepository userRepository,
                       RoleRepository roleRepository,
                       PasswordEncoder passwordEncoder,
                       OAuthAccountRepository oauthAccountRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.oauthAccountRepository = oauthAccountRepository;
    }

    @Transactional
    public AppUser register(RegisterForm form) {
        String email = normalizeEmail(form.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException("Пользователь с таким email уже существует");
        }

        Role roleUser = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Роль ROLE_USER не найдена. Проверь миграции."));

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(form.getPassword()));
        user.getRoles().add(roleUser);

        return userRepository.save(user);
    }

    @Transactional
    public AppUser loginOrRegisterWithGoogle(String googleSub, String email, Boolean emailVerified) {
        if (googleSub == null || googleSub.isBlank()) {
            throw new IllegalArgumentException("Google sub обязателен");
        }

        if (email == null || email.isBlank() || !Boolean.TRUE.equals(emailVerified)) {
            throw new IllegalArgumentException("Для входа через Google нужен подтвержденный email");
        }

        return oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, googleSub)
                .map(OAuthAccount::getUser)
                .orElseGet(() -> createAndLinkGoogleUser(googleSub, email));
    }

    private AppUser createAndLinkGoogleUser(String googleSub, String email) {
        String normalizedEmail = normalizeEmail(email);

        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseGet(() -> createNewOAuthUser(normalizedEmail));

        OAuthAccount account = new OAuthAccount();
        account.setUser(user);
        account.setProvider(OAuthProvider.GOOGLE);
        account.setProviderUserId(googleSub);
        account.setEmailAtProvider(normalizedEmail);

        oauthAccountRepository.save(account);
        return user;
    }

    private AppUser createNewOAuthUser(String email) {
        Role roleUser = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException("Роль ROLE_USER не найдена. Проверь миграции."));

        AppUser user = new AppUser();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(generateUnusablePassword()));
        user.getRoles().add(roleUser);

        return userRepository.save(user);
    }

    private String generateUnusablePassword() {
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public static class EmailAlreadyUsedException extends RuntimeException {
        public EmailAlreadyUsedException(String message) {
            super(message);
        }
    }
}