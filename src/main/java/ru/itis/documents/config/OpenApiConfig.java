package ru.itis.documents.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI semWork3OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SemWork3 Plant Care API")
                        .version("v1")
                        .description("REST API для проекта семестровой работы №3 (уход за растениями)")
                        .contact(new Contact().name("ITIS").url("https://itis.kpfu.ru"))
                        .license(new License().name("MIT")))
                .externalDocs(new ExternalDocumentation()
                        .description("Swagger UI")
                        .url("/swagger-ui/index.html"));
    }
}