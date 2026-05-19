package ru.itis.documents.integration.plantnet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ru.itis.documents.exception.IntegrationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PlantNetClient {

    private static final Logger log = LoggerFactory.getLogger(PlantNetClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.plantnet.apiKey:}")
    private String apiKey;

    @Value("${app.plantnet.project:all}")
    private String project;

    @Value("${app.plantnet.baseUrl:https://my-api.plantnet.org}")
    private String baseUrl;

    @Value("${app.plantnet.lang:en}")
    private String lang;

    @Value("${app.plantnet.nbResults:5}")
    private int nbResults;

    public PlantNetClient(ObjectMapper objectMapper, RestTemplate restTemplate) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    public IdentifyResult identify(byte[] imageBytes, String originalFilename) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IntegrationException(
                    "PLANTNET_NOT_CONFIGURED",
                    "Интеграция Pl@ntNet не настроена (нет API ключа).",
                    Map.of("service", "plantnet"),
                    503
            );
        }

        String url = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .pathSegment("v2", "identify", project)
                .queryParam("api-key", apiKey)
                .queryParam("lang", lang)
                .queryParam("nb-results", nbResults)
                .encode()
                .build()
                .toUriString();

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("images", new NamedByteArrayResource(imageBytes, safeName(originalFilename)));
        body.add("organs", "auto");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
                throw new IntegrationException(
                        "PLANTNET_HTTP_ERROR",
                        "Pl@ntNet вернул ошибку. Попробовать позже.",
                        Map.of("service", "plantnet", "status", resp.getStatusCode().value()),
                        503
                );
            }

            JsonNode root = objectMapper.readTree(resp.getBody());
            String bestMatch = textOrNull(root.get("bestMatch"));

            Integer remaining = null;
            JsonNode remainingNode = root.get("remainingIdentificationRequests");
            if (remainingNode != null && !remainingNode.isNull()) {
                if (remainingNode.canConvertToInt()) {
                    remaining = remainingNode.asInt();
                } else {
                    String txt = remainingNode.asText(null);
                    if (txt != null) {
                        try {
                            remaining = Integer.parseInt(txt.trim());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            List<Candidate> candidates = new ArrayList<>();
            JsonNode results = root.get("results");
            if (results != null && results.isArray()) {
                for (JsonNode r : results) {
                    double score = r.path("score").asDouble();
                    JsonNode species = r.path("species");
                    String sci = textOrNull(species.get("scientificNameWithoutAuthor"));
                    if (sci == null) sci = textOrNull(species.get("scientificName"));

                    List<String> commonNames = new ArrayList<>();
                    JsonNode commons = species.get("commonNames");
                    if (commons != null && commons.isArray()) {
                        for (JsonNode cn : commons) commonNames.add(cn.asText());
                    }

                    if (sci != null) candidates.add(new Candidate(sci, commonNames, score));
                }
            }

            return new IdentifyResult(bestMatch, candidates, remaining, root, resp.getBody());

        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            String bodyStr = e.getResponseBodyAsString();

            String code;
            String msg;

            if (status == 429) {
                code = "PLANTNET_QUOTA_EXCEEDED";
                msg = "Достигнут лимит запросов Pl@ntNet. Попробовать позже.";
            } else if (status >= 500) {
                code = "PLANTNET_UNAVAILABLE";
                msg = "Pl@ntNet временно недоступен. Попробовать позже.";
            } else if (status == 400) {
                code = "PLANTNET_BAD_REQUEST";
                msg = "Pl@ntNet не смог распознать фото (неподходящий формат/контент).";
            } else {
                code = "PLANTNET_HTTP_ERROR";
                msg = "Ошибка Pl@ntNet (" + status + "). Попробовать позже.";
            }

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("service", "plantnet");
            details.put("status", status);
            details.put("response", snippet(bodyStr));

            throw new IntegrationException(code, msg, details, (status >= 500 || status == 429) ? 503 : 400, e);

        } catch (ResourceAccessException e) {
            throw new IntegrationException(
                    "PLANTNET_UNAVAILABLE",
                    "Pl@ntNet не отвечает. Попробовать позже.",
                    Map.of("service", "plantnet", "reason", "timeout_or_network"),
                    503,
                    e
            );

        } catch (RestClientException e) {
            throw new IntegrationException(
                    "PLANTNET_UNAVAILABLE",
                    "Pl@ntNet временно недоступен. Попробовать позже.",
                    Map.of("service", "plantnet", "reason", "rest_client_error"),
                    503,
                    e
            );

        } catch (Exception e) {
            log.error("Failed to parse PlantNet response", e);
            throw new IntegrationException(
                    "PLANTNET_BAD_RESPONSE",
                    "Pl@ntNet вернул неожиданный ответ. Попробовать позже.",
                    Map.of("service", "plantnet", "reason", "bad_response"),
                    503,
                    e
            );
        }
    }

    private static String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private static String safeName(String s) {
        if (s == null || s.isBlank()) return "image.jpg";
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String snippet(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() <= 400) return t;
        return t.substring(0, 400) + "...";
    }

    private static class NamedByteArrayResource extends ByteArrayResource {
        private final String filename;

        private NamedByteArrayResource(byte[] byteArray, String filename) {
            super(byteArray);
            this.filename = filename;
        }

        @Override
        public String getFilename() {
            return filename;
        }
    }

    public record Candidate(String scientificName, List<String> commonNames, double score) {
    }

    public record IdentifyResult(
            String bestMatch,
            List<Candidate> candidates,
            Integer remainingIdentificationRequests,
            JsonNode rawNode,
            String rawJsonString
    ) {
    }
}