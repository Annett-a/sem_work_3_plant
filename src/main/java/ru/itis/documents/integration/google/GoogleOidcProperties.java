package ru.itis.documents.integration.google;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.oauth.google")
public class GoogleOidcProperties {
    private boolean enabled;
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String discoveryUri;
    private String scope;
}