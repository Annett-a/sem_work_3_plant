package ru.itis.documents.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import ru.itis.documents.security.FormTokenInterceptor;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final FormTokenInterceptor formTokenInterceptor;

    public WebMvcConfig(FormTokenInterceptor formTokenInterceptor) {
        this.formTokenInterceptor = formTokenInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(formTokenInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/**",
                        "/v3/api-docs/**",
                        "/swagger-ui/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/error",
                        "/error/**"
                );
    }
}