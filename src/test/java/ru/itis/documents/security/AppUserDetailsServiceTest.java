package ru.itis.documents.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.Role;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.security.AppUserDetailsService;
import ru.itis.documents.security.AppUserPrincipal;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    AppUserRepository userRepository;

    @InjectMocks
    AppUserDetailsService service;

    @Test
    void loadUserByUsername_returnsPrincipalForExistingUser() {
        Role role = new Role();
        role.setName("ROLE_USER");
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setPasswordHash("hash");
        user.setEnabled(true);
        user.getRoles().add(role);
        when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));

        AppUserPrincipal principal = (AppUserPrincipal) service.loadUserByUsername("user@example.com");

        assertThat(principal.getUsername()).isEqualTo("user@example.com");
        assertThat(principal.getPassword()).isEqualTo("hash");
        assertThat(principal.isEnabled()).isTrue();
        assertThat(principal.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void loadUserByUsername_throwsWhenUserMissing() {
        when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("missing@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("missing@example.com");
    }
}
