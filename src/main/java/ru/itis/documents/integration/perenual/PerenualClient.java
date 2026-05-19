package ru.itis.documents.integration.perenual;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.itis.documents.exception.ExternalApiUnavailableException;

import java.util.ArrayList;
import java.util.List;

@Component
public class PerenualClient {

    private static final Logger log = LoggerFactory.getLogger(PerenualClient.class);

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String baseUrl;

    public PerenualClient(
            ObjectMapper objectMapper,
            RestTemplate restTemplate,
            @Value("${app.perenual.apiKey:}") String apiKey,
            @Value("${app.perenual.baseUrl:https://perenual.com/api/v2}") String baseUrl
    ) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
    }

    public List<PerenualSpeciesShort> searchSpecies(String q) {
        if (q == null || q.isBlank()) return List.of();
        ensureApiKey();

        String qNorm = normalizeQuery(q);
        if (qNorm == null || qNorm.isBlank()) return List.of();

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/species-list")
                .queryParam("key", apiKey)
                .queryParam("q", qNorm)
                .build()
                .encode()
                .toUriString();

        String json = doGet(url);

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return List.of();

            List<PerenualSpeciesShort> out = new ArrayList<>();
            for (JsonNode it : data) {
                long id = it.path("id").asLong();
                String commonName = textOrNull(it.get("common_name"));
                List<String> scientificNames = textArray(it.get("scientific_name"));
                String imageUrl = textOrNull(it.path("default_image").get("original_url"));
                out.add(new PerenualSpeciesShort(id, commonName, scientificNames, imageUrl));
            }
            return out;
        } catch (Exception e) {
            log.error("Failed to parse Perenual species-list response", e);
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Perenual вернул неожиданный ответ. Попробовать позже.",
                    503,
                    e
            );
        }
    }

    public PerenualSpeciesDetails getSpeciesDetails(long id) {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        ensureApiKey();

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .path("/species/details/")
                .path(Long.toString(id))
                .queryParam("key", apiKey)
                .build()
                .encode()
                .toUriString();

        String json = doGet(url);

        try {
            JsonNode root = objectMapper.readTree(json);

            long respId = root.path("id").asLong();
            String commonName = textOrNull(root.get("common_name"));
            List<String> scientificNames = textArray(root.get("scientific_name"));
            String description = textOrNull(root.get("description"));
            List<String> sunlight = textArray(root.get("sunlight"));

            PerenualWateringBenchmark wateringBenchmark = parseWateringBenchmark(root.get("watering_general_benchmark"));
            String watering = textOrNull(root.get("watering"));
            String cycle = textOrNull(root.get("cycle"));
            String careLevel = textOrNull(root.get("care_level"));
            String imageUrl = textOrNull(root.path("default_image").get("original_url"));

            return new PerenualSpeciesDetails(
                    respId,
                    commonName,
                    scientificNames,
                    description,
                    cycle,
                    watering,
                    wateringBenchmark,
                    sunlight,
                    careLevel,
                    imageUrl,
                    root
            );
        } catch (Exception e) {
            log.error("Failed to parse Perenual species/details response", e);
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Perenual вернул неожиданный ответ. Попробовать позже.",
                    503,
                    e
            );
        }
    }

    private String doGet(String url) {
        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new ExternalApiUnavailableException(
                        "perenual",
                        "Perenual временно недоступен. Попробовать позже.",
                        resp.getStatusCode().value()
                );
            }
            return resp.getBody();
        } catch (HttpStatusCodeException e) {
            int code = e.getStatusCode().value();
            log.warn("Perenual HTTP {} body={}", code, e.getResponseBodyAsString());

            String msg;
            if (code == 429) msg = "Достигнут лимит запросов к Perenual. Попробовать позже.";
            else if (code >= 500) msg = "Perenual временно недоступен. Попробовать позже.";
            else msg = "Ошибка запроса к Perenual (" + code + "). Попробовать позже.";

            throw new ExternalApiUnavailableException("perenual", msg, code, e);
        } catch (ResourceAccessException e) {
            throw new ExternalApiUnavailableException("perenual", "Perenual не отвечает. Попробовать позже.", 503, e);
        } catch (RestClientException e) {
            throw new ExternalApiUnavailableException("perenual", "Perenual временно недоступен. Попробовать позже.", 503, e);
        }
    }

    private void ensureApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ExternalApiUnavailableException(
                    "perenual",
                    "Интеграция Perenual не настроена (нет ключа).",
                    503
            );
        }
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static List<String> textArray(JsonNode node) {
        if (node == null || node.isNull() || !node.isArray()) return List.of();
        List<String> out = new ArrayList<>();
        for (JsonNode it : node) {
            if (it != null && !it.isNull()) out.add(it.asText());
        }
        return out;
    }

    private static PerenualWateringBenchmark parseWateringBenchmark(JsonNode node) {
        if (node == null || node.isNull()) return null;

        String unit = textOrNull(node.get("unit"));
        Integer minDays = null;
        Integer maxDays = null;

        JsonNode value = node.get("value");
        if (value != null && !value.isNull()) {
            if (value.isTextual()) {
                int[] mm = tryParseRange(value.asText());
                if (mm != null) {
                    minDays = mm[0];
                    maxDays = mm[1];
                }
            } else if (value.isNumber()) {
                minDays = value.asInt();
                maxDays = value.asInt();
            }
        }

        return new PerenualWateringBenchmark(minDays, maxDays, unit);
    }

    private static int[] tryParseRange(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;

        String[] parts = t.split("-");
        try {
            if (parts.length == 2) {
                int a = Integer.parseInt(parts[0].trim());
                int b = Integer.parseInt(parts[1].trim());
                return new int[]{Math.min(a, b), Math.max(a, b)};
            }
            int x = Integer.parseInt(t);
            return new int[]{x, x};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String normalizeQuery(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        int par = s.indexOf('(');
        if (par > 0) s = s.substring(0, par).trim();

        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[,;]+$", "").trim();

        String[] parts = s.split(" ");
        if (parts.length <= 2) return s;

        if (parts.length >= 3 && ("x".equalsIgnoreCase(parts[1]) || "×".equals(parts[1]))) {
            return parts[0] + " " + parts[1] + " " + parts[2];
        }

        return parts[0] + " " + parts[1];
    }
}