package ru.itis.documents.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.StaticHeadersWriter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import ru.itis.documents.dto.ApiErrorResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/auth/**",
                                "/error",
                                "/error/**",
                                "/css/**",
                                "/js/**",
                                "/images/**",
                                "/swagger-ui/**",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/app/species/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/app/species/import").hasRole("ADMIN")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/species/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .defaultSuccessUrl("/app", true)
                        .failureUrl("/auth/login?error")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/?logout")
                )
                .exceptionHandling(eh -> eh
                        .defaultAuthenticationEntryPointFor(
                                (req, res, ex) -> {
                                    log.warn("Unauthorized API access: method={} uri={} qs={}",
                                            req.getMethod(), req.getRequestURI(), req.getQueryString(), ex);
                                    writeApiError(objectMapper, req, res, 401, "UNAUTHORIZED", "Требуется вход");
                                },
                                new AntPathRequestMatcher("/api/**")
                        )
                        .defaultAuthenticationEntryPointFor(
                                new LoginUrlAuthenticationEntryPoint("/auth/login"),
                                AnyRequestMatcher.INSTANCE
                        )
                        .defaultAccessDeniedHandlerFor(
                                (req, res, ex) -> {
                                    String user = (req.getUserPrincipal() == null) ? "anonymous" : req.getUserPrincipal().getName();
                                    log.warn("Forbidden API access: user={} method={} uri={} qs={}",
                                            user, req.getMethod(), req.getRequestURI(), req.getQueryString(), ex);
                                    writeApiError(objectMapper, req, res, 403, "FORBIDDEN", "Доступ запрещён");
                                },
                                new AntPathRequestMatcher("/api/**")
                        )
                        .accessDeniedHandler((req, res, ex) -> {
                            String user = (req.getUserPrincipal() == null) ? "anonymous" : req.getUserPrincipal().getName();
                            log.warn("Access denied (MVC): user={} method={} uri={} qs={}",
                                    user, req.getMethod(), req.getRequestURI(), req.getQueryString(), ex);
                            res.setStatus(403);
                            req.getRequestDispatcher("/error").forward(req, res);
                        })
                )
                .csrf(Customizer.withDefaults())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.sameOrigin())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                        "base-uri 'self'; " +
                                        "form-action 'self'; " +
                                        "frame-ancestors 'self'; " +
                                        "object-src 'none'; " +
                                        "img-src 'self' data: https:; " +
                                        "script-src 'self'; " +
                                        "style-src 'self'"))
                        .addHeaderWriter(new StaticHeadersWriter("Permissions-Policy",
                                "geolocation=(), microphone=(), camera=()"))
                );

        return http.build();
    }

    private static void writeApiError(
            ObjectMapper objectMapper,
            HttpServletRequest req,
            HttpServletResponse res,
            int status,
            String code,
            String message
    ) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", req.getRequestURI());
        details.put("method", req.getMethod());
        if (req.getQueryString() != null && !req.getQueryString().isBlank()) {
            details.put("query", req.getQueryString());
        }

        objectMapper.writeValue(res.getWriter(), new ApiErrorResponse(code, message, details));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Content-Type", "X-Requested-With", "X-CSRF-TOKEN", "Accept", "Origin"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}