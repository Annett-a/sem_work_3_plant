package ru.itis.documents.integration.google;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

@Component
public class GoogleOidcClient {

    private static final String SESSION_STATE = "google.oauth.state";
    private static final String SESSION_CODE_VERIFIER = "google.oauth.code_verifier";

    private final GoogleOidcProperties properties;
    private final RestTemplate restTemplate;

    private volatile GoogleDiscoveryDocument cachedDiscoveryDocument;

    public GoogleOidcClient(GoogleOidcProperties properties, RestTemplate restTemplate) {
        this.properties = properties;
        this.restTemplate = restTemplate;
    }

    public String buildAuthorizationUrl(HttpSession session) {
        ensureEnabled();

        GoogleDiscoveryDocument discovery = getDiscoveryDocument();

        String state = generateRandomUrlSafeString(32);
        String codeVerifier = generateCodeVerifier();
        String codeChallenge = generateCodeChallenge(codeVerifier);

        session.setAttribute(SESSION_STATE, state);
        session.setAttribute(SESSION_CODE_VERIFIER, codeVerifier);

        return UriComponentsBuilder
                .fromHttpUrl(discovery.authorizationEndpoint())
                .queryParam("client_id", properties.getClientId())
                .queryParam("redirect_uri", properties.getRedirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", properties.getScope())
                .queryParam("state", state)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
    }

    public GoogleUserInfoResponse fetchUserInfoByCode(String code, String returnedState, HttpSession session) {
        ensureEnabled();

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Authorization code отсутствует");
        }

        validateState(returnedState, session);

        GoogleDiscoveryDocument discovery = getDiscoveryDocument();
        String codeVerifier = readCodeVerifier(session);

        GoogleTokenResponse tokenResponse = exchangeCodeForToken(discovery, code, codeVerifier);
        GoogleUserInfoResponse userInfo = fetchUserInfo(discovery, tokenResponse.accessToken());

        clearOauthSessionAttributes(session);

        return userInfo;
    }

    private GoogleDiscoveryDocument getDiscoveryDocument() {
        GoogleDiscoveryDocument current = cachedDiscoveryDocument;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (cachedDiscoveryDocument != null) {
                return cachedDiscoveryDocument;
            }

            try {
                ResponseEntity<GoogleDiscoveryDocument> response = restTemplate.getForEntity(
                        properties.getDiscoveryUri(),
                        GoogleDiscoveryDocument.class
                );

                GoogleDiscoveryDocument body = response.getBody();
                if (body == null
                        || isBlank(body.authorizationEndpoint())
                        || isBlank(body.tokenEndpoint())
                        || isBlank(body.userinfoEndpoint())) {
                    throw new IllegalStateException("Google discovery document неполный");
                }

                List<String> methods = body.codeChallengeMethodsSupported();
                if (methods != null && !methods.isEmpty() && !methods.contains("S256")) {
                    throw new IllegalStateException("Google discovery document не поддерживает S256");
                }

                cachedDiscoveryDocument = body;
                return body;
            } catch (RestClientException ex) {
                throw new IllegalStateException("Не удалось загрузить Google discovery document", ex);
            }
        }
    }

    private GoogleTokenResponse exchangeCodeForToken(GoogleDiscoveryDocument discovery,
                                                     String code,
                                                     String codeVerifier) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("code_verifier", codeVerifier);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<GoogleTokenResponse> response = restTemplate.exchange(
                    discovery.tokenEndpoint(),
                    HttpMethod.POST,
                    request,
                    GoogleTokenResponse.class
            );

            GoogleTokenResponse body = response.getBody();
            if (body == null || isBlank(body.accessToken())) {
                throw new IllegalStateException("Google token endpoint не вернул access_token");
            }

            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Не удалось обменять code на token", ex);
        }
    }

    private GoogleUserInfoResponse fetchUserInfo(GoogleDiscoveryDocument discovery, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<GoogleUserInfoResponse> response = restTemplate.exchange(
                    discovery.userinfoEndpoint(),
                    HttpMethod.GET,
                    request,
                    GoogleUserInfoResponse.class
            );

            GoogleUserInfoResponse body = response.getBody();
            if (body == null || isBlank(body.sub()) || isBlank(body.email())) {
                throw new IllegalStateException("Google userinfo вернул неполные данные");
            }

            return body;
        } catch (RestClientException ex) {
            throw new IllegalStateException("Не удалось получить userinfo от Google", ex);
        }
    }

    private void validateState(String returnedState, HttpSession session) {
        Object savedStateObj = session.getAttribute(SESSION_STATE);
        String savedState = savedStateObj instanceof String ? (String) savedStateObj : null;

        if (savedState == null || savedState.isBlank()) {
            throw new IllegalStateException("В сессии отсутствует OAuth state");
        }

        if (returnedState == null || returnedState.isBlank()) {
            throw new IllegalArgumentException("Google не вернул state");
        }

        if (!Objects.equals(savedState, returnedState)) {
            throw new IllegalArgumentException("OAuth state не совпадает");
        }
    }

    private String readCodeVerifier(HttpSession session) {
        Object verifierObj = session.getAttribute(SESSION_CODE_VERIFIER);
        String verifier = verifierObj instanceof String ? (String) verifierObj : null;

        if (verifier == null || verifier.isBlank()) {
            throw new IllegalStateException("В сессии отсутствует code_verifier");
        }

        return verifier;
    }

    private void clearOauthSessionAttributes(HttpSession session) {
        session.removeAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_CODE_VERIFIER);
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Google OAuth отключен");
        }

        if (isBlank(properties.getClientId())) {
            throw new IllegalStateException("Не задан GOOGLE_OAUTH_CLIENT_ID");
        }

        if (isBlank(properties.getClientSecret())) {
            throw new IllegalStateException("Не задан GOOGLE_OAUTH_CLIENT_SECRET");
        }

        if (isBlank(properties.getRedirectUri())) {
            throw new IllegalStateException("Не задан GOOGLE_OAUTH_REDIRECT_URI");
        }

        if (isBlank(properties.getDiscoveryUri())) {
            throw new IllegalStateException("Не задан GOOGLE_OAUTH_DISCOVERY_URI");
        }

        if (isBlank(properties.getScope())) {
            throw new IllegalStateException("Не задан GOOGLE_OAUTH_SCOPE");
        }
    }

    private String generateCodeVerifier() {
        return generateRandomUrlSafeString(64);
    }

    private String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось сгенерировать code_challenge", ex);
        }
    }

    private String generateRandomUrlSafeString(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}