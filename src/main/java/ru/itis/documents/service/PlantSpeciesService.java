package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.domain.enums.LightLevel;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.repository.TagRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlantSpeciesService {

    private final PlantSpeciesRepository plantSpeciesRepository;
    private final CapriciousnessService capriciousnessService;
    private final TagRepository tagRepository;

    private static final Set<String> ALLOWED_FILTER_TAGS = Set.of(
            "яркий свет",
            "полутень",
            "теневыносливое",
            "для новичков",
            "неприхотливое",
            "капризное",
            "засухоустойчивое",
            "влаголюбивое",
            "суккулент",
            "кактус",
            "цветущее",
            "вечнозелёное",
            "декоративно-лиственное",
            "очищает воздух",
            "токсично для животных",
            "безопасно для животных",
            "домашнее",
            "тропическое"
    );

    public List<Tag> getCuratedTagOptions() {
        return tagRepository.findAll().stream()
                .filter(t -> t != null && t.getName() != null)
                .filter(t -> ALLOWED_FILTER_TAGS.contains(t.getName().toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(Tag::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<PlantSpeciesView> listTopCapriciousSpecies(
            String q,
            LightLevel light,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    ) {
        String qNorm = normalize(q);
        qNorm = (qNorm == null) ? "" : qNorm.toLowerCase(Locale.ROOT);

        String tagNorm = normalize(tag);
        tagNorm = (tagNorm == null) ? "" : tagNorm.toLowerCase(Locale.ROOT);

        String lightNorm = (light == null) ? "" : light.ruLabel().toLowerCase(Locale.ROOT);

        int lim = (limit == null || limit <= 0) ? 20 : Math.min(limit, 100);

        return plantSpeciesRepository.findTopCapriciousSpecies(
                        qNorm,
                        tagNorm,
                        lightNorm,
                        maxWaterInterval,
                        PageRequest.of(0, lim)
                ).stream()
                .map(this::toView)
                .toList();
    }


    public List<PlantSpeciesView> listCatalog(String q, LightLevel light, String cap, List<Tag> tags) {
        String qNorm = normalize(q);
        String capNorm = normalize(cap);
        String lightNorm = (light == null) ? null : light.ruLabel();
        if (capNorm != null) capNorm = capNorm.toUpperCase(Locale.ROOT);

        Set<Long> selectedTagIds = (tags == null) ? Set.of() : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Tag::getId)
                .collect(Collectors.toSet());

        String finalCapNorm = capNorm;
        return plantSpeciesRepository.findAll().stream()
                .map(this::toView)
                .filter(s -> qNorm == null
                        || containsIgnoreCase(s.name(), qNorm)
                        || containsIgnoreCase(s.latinName(), qNorm)
                        || containsIgnoreCase(s.description(), qNorm))
                .filter(s -> lightNorm == null
                        || (s.care() != null && containsIgnoreCase(s.care().lightLevel(), lightNorm)))
                .filter(s -> finalCapNorm == null
                        || (s.capriciousness() != null && equalsIgnoreCase(s.capriciousness().key(), finalCapNorm)))
                .filter(s -> selectedTagIds.isEmpty()
                        || (s.tagIds() != null && selectedTagIds.stream().allMatch(id -> s.tagIds().contains(id))))
                .sorted(Comparator.comparing(PlantSpeciesView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public List<PlantSpeciesView> listSuitableForApartment(
            String q,
            String roomLightLevel,
            Integer maxWaterInterval,
            String tag,
            Integer limit
    ) {
        return plantSpeciesRepository.findSuitableForApartment(
                        q,
                        roomLightLevel,
                        maxWaterInterval,
                        tag,
                        limit
                ).stream()
                .map(this::toView)
                .toList();
    }


    public Optional<PlantSpeciesView> getDetails(Long id) {
        return plantSpeciesRepository.findById(id).map(this::toView);
    }

    public List<PlantSpeciesView> listRecentImportedSpecies() {
        return plantSpeciesRepository.findTop12ByExternalIdIsNotNullOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    private PlantSpeciesView toView(PlantSpecies s) {
        CareProfile cp = s.getCareProfile();
        CareProfileView care = null;
        if (cp != null) {
            care = new CareProfileView(
                    cp.getWaterIntervalDays(),
                    cp.getLightLevel(),
                    cp.getHumidityPercent(),
                    cp.getNotes(),
                    extractWateringText(cp)
            );
        }

        List<String> tags = (s.getTags() == null) ? List.of() : s.getTags().stream()
                .map(Tag::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<Long> tagIds = (s.getTags() == null) ? List.of() : s.getTags().stream()
                .map(Tag::getId)
                .filter(id -> id != null)
                .toList();

        CapriciousnessView cap = capriciousnessService.evaluate(s);

        return new PlantSpeciesView(
                s.getId(),
                s.getName(),
                s.getLatinName(),
                s.getImageUrl(),
                s.getDescription(),
                care,
                cap,
                tags,
                tagIds
        );
    }

    private static String extractWateringText(CareProfile cp) {
        if (cp == null) {
            return null;
        }

        if (cp.getWaterIntervalDays() != null) {
            return cp.getWaterIntervalDays() + " дн.";
        }

        String notes = cp.getNotes();
        if (notes == null) {
            return null;
        }

        String prefix = "Watering: ";
        if (!notes.startsWith(prefix)) {
            return null;
        }

        String tail = notes.substring(prefix.length());
        int separatorIndex = tail.indexOf(" • ");

        if (separatorIndex >= 0) {
            return tail.substring(0, separatorIndex);
        }

        return tail;
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) return false;
        return a.equalsIgnoreCase(b);
    }
}