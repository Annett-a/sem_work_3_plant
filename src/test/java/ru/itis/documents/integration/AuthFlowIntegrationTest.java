package ru.itis.documents.integration;
import ru.itis.documents.security.FormTokenService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FormTokenService formTokenService;

    @Test
    void userCanRegisterAndLogin() throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "password123";

        MockHttpSession registerSession = new MockHttpSession();
        String formToken = formTokenService.issueToken(registerSession);

        mockMvc.perform(post("/auth/register")
                        .session(registerSession)
                        .with(csrf())
                        .param(FormTokenService.PARAMETER_NAME, formToken)
                        .param("email", email)
                        .param("password", password)
                        .param("confirmPassword", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login?registered"));

        mockMvc.perform(formLogin("/auth/login")
                        .user(email)
                        .password(password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app"))
                .andExpect(authenticated().withUsername(email));
    }

    @Test
    void seededAdminCanLoginAndOpenAdminPage() throws Exception {
        MvcResult loginResult = mockMvc.perform(formLogin("/auth/login")
                        .user("admin@example.com")
                        .password("admin123"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/app"))
                .andExpect(authenticated().withUsername("admin@example.com"))
                .andExpect(authenticated().withRoles("USER", "ADMIN"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/admin").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/index"));
    }
}