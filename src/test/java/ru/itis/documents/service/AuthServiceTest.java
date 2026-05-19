package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.OAuthAccount;
import ru.itis.documents.domain.entity.Role;
import ru.itis.documents.domain.enums.OAuthProvider;
import ru.itis.documents.dto.RegisterForm;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.OAuthAccountRepository;
import ru.itis.documents.repository.RoleRepository;
import ru.itis.documents.service.AuthService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    AppUserRepository userRepository;
    @Mock
    RoleRepository roleRepository;
    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    OAuthAccountRepository oauthAccountRepository;

    @InjectMocks
    AuthService service;

    @Test
    void register_savesNormalizedEmailEncodedPasswordAndDefaultRole() {
        RegisterForm form = form("  USER@EXAMPLE.COM  ", "secret12");
        Role role = new Role();
        role.setName("ROLE_USER");
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret12")).thenReturn("encoded-secret");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser user = inv.getArgument(0);
            user.setId(11L);
            return user;
        });

        AppUser saved = service.register(form);

        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getEmail()).isEqualTo("user@example.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(saved.getRoles()).containsExactly(role);
    }

    @Test
    void register_throwsWhenEmailAlreadyUsed() {
        RegisterForm form = form("dup@example.com", "secret12");
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(form))
                .isInstanceOf(AuthService.EmailAlreadyUsedException.class)
                .hasMessageContaining("email");
    }

    @Test
    void register_throwsWhenDefaultRoleMissing() {
        RegisterForm form = form("user@example.com", "secret12");
        when(userRepository.existsByEmailIgnoreCase("user@example.com")).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(form))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_USER");
    }

    @Test
    void register_allowsNullEmailAndStillSavesUser() {
        RegisterForm form = form(null, "secret12");

        Role role = new Role();
        role.setName("ROLE_USER");

        when(userRepository.existsByEmailIgnoreCase(null)).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("secret12")).thenReturn("encoded-secret");
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

        AppUser saved = service.register(form);

        assertThat(saved.getEmail()).isNull();
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-secret");
        assertThat(saved.getRoles()).containsExactly(role);
    }

    @Test
    void loginOrRegisterWithGoogle_returnsLinkedUserWhenOauthAccountExists() {
        AppUser linkedUser = new AppUser();
        linkedUser.setId(21L);
        linkedUser.setEmail("linked@example.com");

        OAuthAccount account = new OAuthAccount();
        account.setUser(linkedUser);
        account.setProvider(OAuthProvider.GOOGLE);
        account.setProviderUserId("google-sub-1");
        account.setEmailAtProvider("linked@example.com");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-1"))
                .thenReturn(Optional.of(account));

        AppUser result = service.loginOrRegisterWithGoogle("google-sub-1", "linked@example.com", true);

        assertThat(result).isSameAs(linkedUser);
        verify(userRepository, never()).save(any(AppUser.class));
        verify(oauthAccountRepository, never()).save(any(OAuthAccount.class));
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenGoogleSubIsNull() {
        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle(null, "user@example.com", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Google sub");
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenGoogleSubIsBlank() {
        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle("   ", "user@example.com", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Google sub");
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenEmailIsNull() {
        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle("google-sub-1", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("подтвержденный email");
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenEmailIsBlank() {
        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle("google-sub-1", "   ", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("подтвержденный email");
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenEmailIsNotVerified() {
        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle("google-sub-1", "user@example.com", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("подтвержденный email");
    }

    @Test
    void loginOrRegisterWithGoogle_linksOauthAccountToExistingUserByNormalizedEmail() {
        AppUser existingUser = new AppUser();
        existingUser.setId(31L);
        existingUser.setEmail("user@example.com");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-2"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(existingUser));
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AppUser result = service.loginOrRegisterWithGoogle("google-sub-2", "  USER@EXAMPLE.COM  ", true);

        assertThat(result).isSameAs(existingUser);

        verify(userRepository, never()).save(any(AppUser.class));
        verify(oauthAccountRepository).save(any(OAuthAccount.class));
        verify(oauthAccountRepository).save(argThat(account ->
                account.getUser() == existingUser
                        && account.getProvider() == OAuthProvider.GOOGLE
                        && "google-sub-2".equals(account.getProviderUserId())
                        && "user@example.com".equals(account.getEmailAtProvider())
        ));
    }

    @Test
    void loginOrRegisterWithGoogle_createsNewUserAndOauthAccountWhenUserDoesNotExist() {
        Role role = new Role();
        role.setName("ROLE_USER");

        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-3"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("newuser@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER"))
                .thenReturn(Optional.of(role));
        when(passwordEncoder.encode(any(String.class)))
                .thenReturn("encoded-generated-password");
        when(userRepository.save(any(AppUser.class)))
                .thenAnswer(inv -> {
                    AppUser user = inv.getArgument(0);
                    user.setId(41L);
                    return user;
                });
        when(oauthAccountRepository.save(any(OAuthAccount.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        AppUser result = service.loginOrRegisterWithGoogle("google-sub-3", "  NewUser@Example.com  ", true);

        assertThat(result.getId()).isEqualTo(41L);
        assertThat(result.getEmail()).isEqualTo("newuser@example.com");
        assertThat(result.getPasswordHash()).isEqualTo("encoded-generated-password");
        assertThat(result.getRoles()).containsExactly(role);

        verify(passwordEncoder).encode(argThat(value -> value != null && !value.toString().isBlank()));
        verify(oauthAccountRepository).save(argThat(account ->
                account.getUser() == result
                        && account.getProvider() == OAuthProvider.GOOGLE
                        && "google-sub-3".equals(account.getProviderUserId())
                        && "newuser@example.com".equals(account.getEmailAtProvider())
        ));
    }

    @Test
    void loginOrRegisterWithGoogle_throwsWhenDefaultRoleMissingForNewOauthUser() {
        when(oauthAccountRepository.findByProviderAndProviderUserId(OAuthProvider.GOOGLE, "google-sub-4"))
                .thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("newuser@example.com"))
                .thenReturn(Optional.empty());
        when(roleRepository.findByName("ROLE_USER"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginOrRegisterWithGoogle("google-sub-4", "newuser@example.com", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ROLE_USER");
    }

    private RegisterForm form(String email, String password) {
        RegisterForm form = new RegisterForm();
        form.setEmail(email);
        form.setPassword(password);
        form.setConfirmPassword(password);
        return form;
    }
}
