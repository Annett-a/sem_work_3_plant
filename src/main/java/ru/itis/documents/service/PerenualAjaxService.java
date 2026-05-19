package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.dto.view.PerenualImportedSpeciesView;
import ru.itis.documents.dto.view.PerenualPreviewView;
import ru.itis.documents.dto.view.PerenualSearchCardView;
import ru.itis.documents.exception.IntegrationException;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesShort;
import ru.itis.documents.repository.PlantSpeciesRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class PerenualAjaxService {

    private final PerenualClient perenualClient;
    private final PerenualImportService perenualImportService;
    private final PlantSpeciesRepository plantSpeciesRepository;
    private final CapriciousnessService capriciousnessService;

    @Transactional
    public PerenualPreviewView previewByScientificName(String scientificNameRaw) {
        String query = PlantRecognitionImportService.normalizeScientificNameForSearch(scientificNameRaw);
        if (query == null || query.isBlank()) {
            throw new IntegrationException(
                    "BAD_REQUEST",
                    "Не получилось подготовить запрос из имени: " + scientificNameRaw,
                    null,
                    400
            );
        }

        PerenualSearchCardView local = findLocalCard(scientificNameRaw);
        if (local != null && local.localSpeciesId() != null) {
            return new PerenualPreviewView(
                    query,
                    local.perenualId(),
                    local.name(),
                    local.scientificName(),
                    local.scientificNames(),
                    local.imageUrl(),
                    true,
                    local.localSpeciesId()
            );
        }

        SearchAttempt attempt = searchWithFallback(query);
        List<PerenualSpeciesShort> found = attempt.results();
        if (found.isEmpty()) {
            throw new IntegrationException(
                    "PLANT_NOT_FOUND",
                    "Растение не найдено",
                    null,
                    404
            );
        }

        long perenualId = chooseBestPerenualId(query, found);
        PerenualSpeciesShort chosen = found.stream()
                .filter(x -> x.id() == perenualId)
                .findFirst()
                .orElse(found.get(0));

        String sci = firstScientific(chosen.scientificNames());
        String name = firstNonBlank(chosen.commonName(), sci, "Вид #" + perenualId);

        boolean already = plantSpeciesRepository.existsByExternalId(perenualId);
        Long localId = null;
        if (already) {
            localId = plantSpeciesRepository.findByExternalId(perenualId)
                    .map(PlantSpecies::getId)
                    .orElse(null);
        }

        return new PerenualPreviewView(
                query,
                perenualId,
                name,
                sci,
                chosen.scientificNames(),
                chosen.imageUrl(),
                already,
                localId
        );
    }

    @Transactional
    public PerenualSearchCardView searchCard(String queryRaw) {
        String rawQuery = normalizeText(queryRaw);
        if (rawQuery == null) {
            throw new IntegrationException(
                    "BAD_REQUEST",
                    "Введите название растения",
                    null,
                    400
            );
        }

        PerenualSearchCardView local = findLocalCard(rawQuery);
        if (local != null) {
            return local;
        }

        String query = PlantRecognitionImportService.normalizeScientificNameForSearch(rawQuery);
        if (query == null || query.isBlank()) {
            throw new IntegrationException(
                    "BAD_REQUEST",
                    "Не получилось подготовить запрос из имени: " + rawQuery,
                    null,
                    400
            );
        }

        SearchAttempt attempt = searchWithFallback(query);
        List<PerenualSpeciesShort> found = attempt.results();

        if (found.isEmpty()) {
            throw new IntegrationException(
                    "PLANT_NOT_FOUND",
                    "Растение не найдено",
                    null,
                    404
            );
        }

        long perenualId = chooseBestPerenualId(query, found);

        PlantSpecies saved = perenualImportService.importIfMissing(perenualId);

        return toLocalCard(rawQuery, saved);
    }

    @Transactional
    public PerenualImportedSpeciesView importByPerenualId(long perenualId) {
        PlantSpecies sp = perenualImportService.importIfMissing(perenualId);
        return new PerenualImportedSpeciesView(
                perenualId,
                sp.getId(),
                sp.getName(),
                sp.getLatinName()
        );
    }

    private PerenualSearchCardView findLocalCard(String rawQuery) {
        String q = rawQuery.toLowerCase(Locale.ROOT);
        String normalizedScientific = PlantRecognitionImportService.normalizeScientificNameForSearch(rawQuery);
        String qSci = normalizedScientific == null ? null : normalizedScientific.toLowerCase(Locale.ROOT);

        return plantSpeciesRepository.findAll().stream()
                .map(sp -> new LocalMatch(sp, scoreLocal(sp, q, qSci)))
                .filter(m -> m.score() > 0)
                .sorted(Comparator
                        .comparingInt(LocalMatch::score).reversed()
                        .thenComparing(m -> safeLower(m.species().getLatinName()))
                        .thenComparing(m -> safeLower(m.species().getName())))
                .map(m -> toLocalCard(rawQuery, perenualImportService.refreshImageOnceIfNeeded(m.species())))
                .findFirst()
                .orElse(null);
    }

    private int scoreLocal(PlantSpecies sp, String q, String qSci) {
        String name = normalizeText(sp.getName());
        String latin = normalizeText(sp.getLatinName());

        String nameLower = name == null ? null : name.toLowerCase(Locale.ROOT);
        String latinLower = latin == null ? null : latin.toLowerCase(Locale.ROOT);

        String latinNorm = PlantRecognitionImportService.normalizeScientificNameForSearch(sp.getLatinName());
        String latinNormLower = latinNorm == null ? null : latinNorm.toLowerCase(Locale.ROOT);

        if (latinNormLower != null && qSci != null && latinNormLower.equals(qSci)) return 100;
        if (latinLower != null && latinLower.equals(q)) return 95;
        if (nameLower != null && nameLower.equals(q)) return 90;

        if (latinNormLower != null && qSci != null && latinNormLower.startsWith(qSci)) return 85;
        if (latinLower != null && latinLower.startsWith(q)) return 80;
        if (nameLower != null && nameLower.startsWith(q)) return 75;

        if (latinNormLower != null && qSci != null && latinNormLower.contains(qSci)) return 70;
        if (latinLower != null && latinLower.contains(q)) return 65;
        if (nameLower != null && nameLower.contains(q)) return 60;

        return 0;
    }

    private PerenualSearchCardView toLocalCard(String query, PlantSpecies sp) {
        CareProfile cp = sp.getCareProfile();

        Integer waterDays = cp == null ? null : cp.getWaterIntervalDays();
        String watering = (waterDays == null) ? null : "каждые " + waterDays + " дн.";

        List<String> sunlight = (cp == null || cp.getLightLevel() == null || cp.getLightLevel().isBlank())
                ? List.of()
                : List.of(cp.getLightLevel());

        CapriciousnessView cap = capriciousnessService.evaluate(sp);
        String careLevel = cap == null ? null : cap.label();

        String scientificName = firstNonBlank(sp.getLatinName(), sp.getName());
        List<String> scientificNames = (sp.getLatinName() == null || sp.getLatinName().isBlank())
                ? List.of()
                : List.of(sp.getLatinName());

        return new PerenualSearchCardView(
                query,
                sp.getExternalId() == null ? 0L : sp.getExternalId(),
                firstNonBlank(sp.getName(), sp.getLatinName(), "Растение"),
                scientificName,
                scientificNames,
                sp.getDescription(),
                null,
                watering,
                waterDays,
                waterDays,
                careLevel,
                sunlight,
                sp.getImageUrl(),
                true,
                sp.getId()
        );
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

    private record LocalMatch(PlantSpecies species, int score) {
    }

    private static long chooseBestPerenualId(String normalizedQuery, List<PerenualSpeciesShort> results) {
        String q = normalizedQuery.trim().toLowerCase(Locale.ROOT);

        for (PerenualSpeciesShort r : results) {
            for (String sci : r.scientificNames()) {
                String n = PlantRecognitionImportService.normalizeScientificNameForSearch(sci);
                if (n != null && n.trim().toLowerCase(Locale.ROOT).equals(q)) {
                    return r.id();
                }
            }
        }
        return results.get(0).id();
    }

    private static String firstScientific(List<String> scientificNames) {
        if (scientificNames == null || scientificNames.isEmpty()) return null;
        for (String s : scientificNames) {
            if (s != null && !s.isBlank()) return s.trim();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v == null) continue;
            String t = v.trim();
            if (!t.isEmpty()) return t;
        }
        return null;
    }

    private static String normalizeText(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}