package ru.itis.documents.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class FormTokenService {

    public static final String PARAMETER_NAME = "_formToken";

    private static final String SESSION_ATTRIBUTE = FormTokenService.class.getName() + ".TOKENS";
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKENS_IN_SESSION = 40;

    private final SecureRandom secureRandom = new SecureRandom();

    public String issueToken(HttpSession session) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        Set<String> tokens = getOrCreateTokens(session);

        while (tokens.size() >= MAX_TOKENS_IN_SESSION) {
            Iterator<String> iterator = tokens.iterator();
            if (!iterator.hasNext()) {
                break;
            }
            iterator.next();
            iterator.remove();
        }

        tokens.add(token);
        return token;
    }

    public boolean consumeToken(HttpSession session, String token) {
        if (session == null || token == null || token.isBlank()) {
            return false;
        }

        Object raw = session.getAttribute(SESSION_ATTRIBUTE);
        if (!(raw instanceof Set<?> rawSet)) {
            return false;
        }

        @SuppressWarnings("unchecked")
        Set<String> tokens = (Set<String>) rawSet;

        return tokens.remove(token);
    }

    private Set<String> getOrCreateTokens(HttpSession session) {
        Object raw = session.getAttribute(SESSION_ATTRIBUTE);
        if (raw instanceof Set<?> rawSet) {
            @SuppressWarnings("unchecked")
            Set<String> tokens = (Set<String>) rawSet;
            return tokens;
        }

        Set<String> tokens = new LinkedHashSet<>();
        session.setAttribute(SESSION_ATTRIBUTE, tokens);
        return tokens;
    }
}