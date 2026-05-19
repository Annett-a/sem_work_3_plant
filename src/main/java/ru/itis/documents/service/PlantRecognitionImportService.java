package ru.itis.documents.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesShort;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.PlantIdentificationRepository;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PlantRecognitionImportService {

    private final AppUserRepository appUserRepository;
    private final PlantIdentificationRepository plantIdentificationRepository;

    private final PerenualClient perenualClient;
    private final PerenualImportService perenualImportService;

    @Transactional
    public Long importSelectedCandidateToCatalog(String username, Long identificationId) {
        return importSelectedCandidateToCatalog(username, identificationId, null);
    }

    @Transactional
    public Long importSelectedCandidateToCatalog(String username, Long identificationId, Long perenualId) {
        AppUser user = resolveUser(username);

        PlantIdentification pi = plantIdentificationRepository.findByIdAndUser_Id(identificationId, user.getId())
                .orElseThrow(() -> new PlantIdentificationNotFoundException("Распознавание не найдено"));

        if (perenualId != null && perenualId > 0) {
            return perenualImportService.importIfMissing(perenualId).getId();
        }

        String selected = pi.getSelectedScientificName();
        if (selected == null || selected.isBlank()) {
            throw new IllegalStateException("Сначала нужно выбрать кандидата.");
        }

        String query = normalizeScientificNameForSearch(selected);
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("Не получилось подготовить запрос в Perenual из выбранного имени: " + selected);
        }

        SearchAttempt attempt = searchWithFallback(query);
        List<PerenualSpeciesShort> found = attempt.results();
        if (found.isEmpty()) {
            if (!attempt.usedQuery().equalsIgnoreCase(query)) {
                throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query
                        + " (также пробовали: " + attempt.usedQuery() + ")");
            }
            throw new IllegalArgumentException("Perenual не нашёл вид по запросу: " + query);
        }

        long resolvedPerenualId = chooseBestPerenualId(query, found);
        return perenualImportService.importIfMissing(resolvedPerenualId).getId();
    }

    private long chooseBestPerenualId(String normalizedQuery, List<PerenualSpeciesShort> results) {
        String q = normalizedQuery.trim().toLowerCase(Locale.ROOT);

        for (PerenualSpeciesShort r : results) {
            for (String sci : r.scientificNames()) {
                String n = normalizeScientificNameForSearch(sci);
                if (n != null && n.trim().toLowerCase(Locale.ROOT).equals(q)) {
                    return r.id();
                }
            }
        }

        return results.get(0).id();
    }

    private SearchAttempt searchWithFallback(String normalizedQuery) {
        String q = normalizedQuery.trim();

        List<PerenualSpeciesShort> found = perenualClient.searchSpecies(q);
        if (!found.isEmpty()) {
            return new SearchAttempt(q, found);
        }

        String genus = q.split("\\s+")[0].trim();
        if (genus.isEmpty() || genus.equalsIgnoreCase(q)) {
            return new SearchAttempt(q, found);
        }

        List<PerenualSpeciesShort> byGenus = perenualClient.searchSpecies(genus);
        return new SearchAttempt(genus, byGenus);
    }

    private record SearchAttempt(String usedQuery, List<PerenualSpeciesShort> results) {
    }

    static String normalizeScientificNameForSearch(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;

        int par = s.indexOf('(');
        if (par > 0) s = s.substring(0, par).trim();

        s = s.replaceAll("\\s+", " ").trim();
        s = s.replaceAll("[,;]+$", "").trim();

        if (s.isEmpty()) return null;

        String[] parts = s.split(" ");
        if (parts.length == 1) return parts[0];

        if (parts.length >= 3 && ("x".equalsIgnoreCase(parts[1]) || "×".equals(parts[1]))) {
            return parts[0] + " " + parts[1] + " " + parts[2];
        }

        return parts[0] + " " + parts[1];
    }

    private AppUser resolveUser(String username) {
        return appUserRepository.findByEmailIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
    }
}