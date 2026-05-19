package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesDetails;
import ru.itis.documents.integration.perenual.PerenualWateringBenchmark;
import ru.itis.documents.repository.CareProfileRepository;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.repository.TagRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PerenualImportService {

    private final PlantSpeciesRepository plantSpeciesRepository;
    private final CareProfileRepository careProfileRepository;
    private final TagRepository tagRepository;
    private final PerenualClient perenualClient;

    @Transactional
    public PlantSpecies importIfMissing(long perenualSpeciesId) {
        if (perenualSpeciesId <= 0) {
            throw new IllegalArgumentException("perenualSpeciesId must be positive");
        }

        var existing = plantSpeciesRepository.findByExternalId(perenualSpeciesId);
        if (existing.isPresent()) {
            return refreshImageOnceIfNeeded(existing.get());
        }

        PerenualSpeciesDetails d = perenualClient.getSpeciesDetails(perenualSpeciesId);

        PlantSpecies species = new PlantSpecies();
        species.setExternalId(perenualSpeciesId);

        String name = firstNonBlank(
                d.commonName(),
                firstScientific(d.scientificNames()),
                "Вид #" + perenualSpeciesId
        );

        species.setName(name);
        species.setLatinName(firstScientific(d.scientificNames()));
        species.setDescription(buildCardDescription(d));
        species.setImageUrl(normalizeText(d.imageUrl()));
        species.setImageLookupAttempted(true);

        species = plantSpeciesRepository.save(species);

        CareProfile careProfile = new CareProfile();
        careProfile.setSpecies(species);
        careProfile.setWaterIntervalDays(guessWaterIntervalDays(d.wateringBenchmark()));
        careProfile.setLightLevel(mapLightLevel(d.sunlight()));
        careProfile.setNotes(buildCareNotes(d));
        careProfileRepository.save(careProfile);

        Set<Tag> tags = new HashSet<>();
        fillDerivedTags(tags, d, careProfile);

        if (tags.isEmpty()) {
            tags.add(ensureTag("тропическое"));
        }

        species.getTags().addAll(tags);
        return plantSpeciesRepository.save(species);
    }

    @Transactional
    public PlantSpecies refreshImageOnceIfNeeded(PlantSpecies species) {
        if (species == null) {
            return null;
        }

        if (normalizeText(species.getImageUrl()) != null) {
            return species;
        }

        if (species.getExternalId() == null || species.getExternalId() <= 0) {
            return species;
        }

        if (species.isImageLookupAttempted()) {
            return species;
        }

        try {
            PerenualSpeciesDetails d = perenualClient.getSpeciesDetails(species.getExternalId());

            String imageUrl = normalizeText(d.imageUrl());
            if (imageUrl != null) {
                species.setImageUrl(imageUrl);
            }

            species.setImageLookupAttempted(true);
            return plantSpeciesRepository.save(species);
        } catch (RuntimeException e) {
            return species;
        }
    }

    private Tag ensureTag(String name) {
        return tagRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Tag t = new Tag();
                    t.setName(name);
                    return tagRepository.save(t);
                });
    }

    private void fillDerivedTags(Set<Tag> tags, PerenualSpeciesDetails d, CareProfile careProfile) {
        addLightTags(tags, careProfile);
        addCareTags(tags, d);
        addWateringTags(tags, d, careProfile);
        addDescriptionTags(tags, d);
    }

    private void addLightTags(Set<Tag> tags, CareProfile careProfile) {
        String lightTag = careProfile.getLightLevel();
        if (lightTag != null) {
            tags.add(ensureTag(lightTag));
        }
    }

    private void addCareTags(Set<Tag> tags, PerenualSpeciesDetails d) {
        String care = normalizeLower(d.careLevel());
        if (care == null) return;

        if (containsAny(care, "easy", "low", "begin")) {
            tags.add(ensureTag("для новичков"));
            tags.add(ensureTag("неприхотливое"));
        } else if (containsAny(care, "hard", "high", "difficult")) {
            tags.add(ensureTag("капризное"));
        }
    }

    private void addWateringTags(Set<Tag> tags, PerenualSpeciesDetails d, CareProfile careProfile) {
        Integer waterDays = careProfile.getWaterIntervalDays();

        if (waterDays != null) {
            if (waterDays >= 12) {
                tags.add(ensureTag("засухоустойчивое"));
            } else if (waterDays <= 5) {
                tags.add(ensureTag("влаголюбивое"));
            }
            return;
        }

        String watering = normalizeLower(d.watering());
        if (watering == null) return;

        if (containsAny(watering, "minimum", "low")) {
            tags.add(ensureTag("засухоустойчивое"));
        } else if (containsAny(watering, "frequent", "high")) {
            tags.add(ensureTag("влаголюбивое"));
        }
    }

    private void addDescriptionTags(Set<Tag> tags, PerenualSpeciesDetails d) {
        String desc = normalizeLower(d.description());
        if (desc == null) return;

        if (containsAny(desc, "succulent")) {
            tags.add(ensureTag("суккулент"));
        }

        if (containsAny(desc, "cactus", "cacti")) {
            tags.add(ensureTag("кактус"));
        }

        if (containsAny(desc, "flower", "flowers", "bloom", "blossom", "flowering")) {
            tags.add(ensureTag("цветущее"));
        }

        if (containsAny(desc, "air purif", "clean air", "air-clean")) {
            tags.add(ensureTag("очищает воздух"));
        }

        if (containsAny(desc, "toxic", "poison", "poisonous")) {
            tags.add(ensureTag("токсично для животных"));
        }

        if (containsAny(desc, "pet", "pets", "animal", "animals", "cat", "dog")
                && containsAny(desc, "safe", "non-toxic", "nontoxic", "harmless")) {
            tags.add(ensureTag("безопасно для животных"));
        }

        if (containsAny(desc, "evergreen")) {
            tags.add(ensureTag("вечнозелёное"));
        }

        if (containsAny(desc, "fast-growing", "fast growing", "rapid growth")) {
            tags.add(ensureTag("быстрорастущее"));
        }

        if (containsAny(desc, "foliage", "ornamental leaves", "decorative leaves")) {
            tags.add(ensureTag("декоративно-лиственное"));
        }
    }


    private static Integer guessWaterIntervalDays(PerenualWateringBenchmark wb) {
        if (wb == null) return null;

        Integer a = wb.minValue();
        Integer b = wb.maxValue();

        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;

        return Math.max(1, (a + b) / 2);
    }

    private static String mapLightLevel(List<String> sunlight) {
        if (sunlight == null || sunlight.isEmpty()) return null;

        String joined = String.join(" ", sunlight).toLowerCase(Locale.ROOT);

        if (joined.contains("full") && joined.contains("sun")) return "яркий свет";
        if (joined.contains("part") && (joined.contains("shade") || joined.contains("sun"))) return "полутень";
        if (joined.contains("shade")) return "теневыносливое";

        return null;
    }

    private static String buildCardDescription(PerenualSpeciesDetails d) {
        List<String> parts = new ArrayList<>();

        String careText = carePhrase(d.careLevel());
        String lightText = lightPhrase(d.sunlight());
        String wateringText = wateringPhrase(d.wateringBenchmark(), d.watering());

        if (careText != null && lightText != null) {
            parts.add(careText + " for " + lightText + ".");
        } else if (careText != null) {
            parts.add(careText + ".");
        } else if (lightText != null) {
            parts.add("Plant that prefers " + lightText + ".");
        }

        if (wateringText != null) {
            parts.add(wateringText + ".");
        }

        String raw = normalizeText(d.description());
        if (raw != null) {
            parts.add(raw);
        }

        if (parts.isEmpty()) {
            return raw;
        }

        return String.join(" ", parts);
    }

    private static String buildCareNotes(PerenualSpeciesDetails d) {
        List<String> notes = new ArrayList<>();

        String watering = normalizeText(d.watering());
        if (watering != null) {
            notes.add("Watering: " + watering);
        }

        String cycle = normalizeText(d.cycle());
        if (cycle != null) {
            notes.add("Cycle: " + cycle);
        }

        String care = normalizeText(d.careLevel());
        if (care != null) {
            notes.add("Care level: " + care);
        }

        return notes.isEmpty() ? "Imported from Perenual" : String.join(" • ", notes);
    }


    private static String carePhrase(String careLevel) {
        String care = normalizeLower(careLevel);
        if (care == null) return null;

        if (care.contains("easy") || care.contains("low") || care.contains("begin")) {
            return "Low-maintenance plant";
        }
        if (care.contains("medium") || care.contains("moderate")) {
            return "Plant with moderate care needs";
        }
        if (care.contains("hard") || care.contains("high") || care.contains("difficult")) {
            return "Plant with higher care needs";
        }

        return null;
    }

    private static String lightPhrase(List<String> sunlight) {
        if (sunlight == null || sunlight.isEmpty()) return null;

        String joined = String.join(" ", sunlight).toLowerCase(Locale.ROOT);

        if (joined.contains("full") && joined.contains("sun")) return "bright light";
        if (joined.contains("part") && (joined.contains("shade") || joined.contains("sun"))) return "partial shade";
        if (joined.contains("shade")) return "shade";

        return normalizeText(sunlight.get(0));
    }

    private static String wateringPhrase(PerenualWateringBenchmark wb, String watering) {
        if (wb != null) {
            Integer min = wb.minValue();
            Integer max = wb.maxValue();

            if (min != null && max != null) {
                if (min.equals(max)) {
                    return "Water about every " + min + " days";
                }
                return "Water about every " + min + "–" + max + " days";
            }
        }

        String w = normalizeText(watering);
        if (w != null) {
            return "Watering: " + w;
        }

        return null;
    }

    private static String firstSentence(String text, int maxLen) {
        String t = normalizeText(text);
        if (t == null) return null;

        int dot = t.indexOf('.');
        String result = (dot > 0) ? t.substring(0, dot + 1).trim() : t;

        if (result.length() > maxLen) {
            result = result.substring(0, maxLen).trim() + "...";
        }

        return result;
    }

    private static String normalizeText(String s) {
        if (s == null) return null;

        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    private static String normalizeLower(String s) {
        String t = normalizeText(s);
        return t == null ? null : t.toLowerCase(Locale.ROOT);
    }

    private static String firstScientific(List<String> scientificNames) {
        if (scientificNames == null || scientificNames.isEmpty()) return null;

        for (String s : scientificNames) {
            if (s != null && !s.isBlank()) {
                return s.trim();
            }
        }

        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null) {
                String t = v.trim();
                if (!t.isEmpty()) {
                    return t;
                }
            }
        }

        return null;
    }

    private static boolean containsAny(String text, String... parts) {
        if (text == null) return false;

        for (String part : parts) {
            if (part != null && text.contains(part)) {
                return true;
            }
        }
        return false;
    }

}