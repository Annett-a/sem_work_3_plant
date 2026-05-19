package ru.itis.documents.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesDetails;
import ru.itis.documents.integration.perenual.PerenualWateringBenchmark;
import ru.itis.documents.repository.CareProfileRepository;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.repository.TagRepository;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerenualImportServiceTest {

    @Mock
    PlantSpeciesRepository plantSpeciesRepository;
    @Mock
    CareProfileRepository careProfileRepository;
    @Mock
    TagRepository tagRepository;
    @Mock
    PerenualClient perenualClient;

    @InjectMocks
    PerenualImportService service;

    private final Map<String, Tag> tags = new HashMap<>();
    private long nextTagId = 1L;
    private long nextSpeciesId = 100L;

    @BeforeEach
    void setupTagSave() {
        tags.clear();
        nextTagId = 1L;
        nextSpeciesId = 100L;
        lenient().when(tagRepository.findByNameIgnoreCase(any())).thenAnswer(inv ->
                Optional.ofNullable(tags.get(((String) inv.getArgument(0)).toLowerCase())));
        lenient().when(tagRepository.save(any(Tag.class))).thenAnswer(inv -> {
            Tag tag = inv.getArgument(0);
            if (tag.getId() == null) {
                tag.setId(nextTagId++);
            }
            tags.put(tag.getName().toLowerCase(), tag);
            return tag;
        });
        lenient().when(plantSpeciesRepository.save(any(PlantSpecies.class))).thenAnswer(inv -> {
            PlantSpecies species = inv.getArgument(0);
            if (species.getId() == null) {
                species.setId(nextSpeciesId++);
            }
            return species;
        });
        lenient().when(careProfileRepository.save(any(CareProfile.class))).thenAnswer(inv -> {
            CareProfile cp = inv.getArgument(0);
            if (cp.getSpecies() != null) {
                cp.getSpecies().setCareProfile(cp);
            }
            return cp;
        });
    }

    @Test
    void importIfMissing_throwsForNonPositiveId() {
        assertThatThrownBy(() -> service.importIfMissing(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void importIfMissing_returnsExistingWithoutCallingApi() {
        PlantSpecies existing = new PlantSpecies();
        existing.setId(5L);
        when(plantSpeciesRepository.findByExternalId(99L)).thenReturn(Optional.of(existing));

        PlantSpecies result = service.importIfMissing(99L);

        assertThat(result).isSameAs(existing);
        verify(perenualClient, never()).getSpeciesDetails(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void importIfMissing_importsWhenFindByExternalIdReturnsEmpty() {
        when(plantSpeciesRepository.findByExternalId(99L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(99L)).thenReturn(new PerenualSpeciesDetails(
                99L,
                "Test plant",
                List.of("Testus plantus"),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(99L);

        assertThat(result.getExternalId()).isEqualTo(99L);
        assertThat(result.getName()).isEqualTo("Test plant");
        assertThat(result.getLatinName()).isEqualTo("Testus plantus");
    }

    @Test
    void importIfMissing_importsSpeciesBuildsCareProfileAndDerivesTags() {
        when(plantSpeciesRepository.findByExternalId(7L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(7L)).thenReturn(new PerenualSpeciesDetails(
                7L,
                "Rubber plant",
                List.of("Ficus elastica"),
                "Flowering tropical foliage plant that helps clean air and is toxic to pets.",
                "perennial",
                "Frequent",
                new PerenualWateringBenchmark(3, 5, "days"),
                List.of("full sun", "part shade"),
                "high",
                "http://img",
                null
        ));

        PlantSpecies result = service.importIfMissing(7L);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("Rubber plant");
        assertThat(result.getLatinName()).isEqualTo("Ficus elastica");
        assertThat(result.getDescription()).contains("higher care needs");
        assertThat(result.getTags()).extracting(Tag::getName)
                .contains("капризное", "влаголюбивое", "яркий свет", "цветущее", "очищает воздух", "токсично для животных");
    }

    @Test
    void importIfMissing_usesFallbackNameAndDefaultTropicalTagWhenNothingElseDerived() {
        when(plantSpeciesRepository.findByExternalId(8L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(8L)).thenReturn(new PerenualSpeciesDetails(
                8L,
                null,
                List.of(),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(8L);

        assertThat(result.getName()).isEqualTo("Вид #8");
        assertThat(result.getTags()).extracting(Tag::getName).containsExactly("тропическое");
    }

    @Test
    void importIfMissing_derivesBeginnerAndDroughtTagsFromCareLevelAndWateringText() {
        when(plantSpeciesRepository.findByExternalId(9L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(9L)).thenReturn(new PerenualSpeciesDetails(
                9L,
                "Aloe",
                List.of("Aloe vera"),
                "succulent evergreen with decorative leaves",
                "perennial",
                "minimum",
                null,
                List.of("shade"),
                "easy",
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(9L);

        assertThat(result.getTags()).extracting(Tag::getName)
                .contains("для новичков", "неприхотливое", "засухоустойчивое", "теневыносливое", "суккулент", "вечнозелёное", "декоративно-лиственное");
    }

    @Test
    void importIfMissing_usesScientificNameForBlankCommonNameAndBuildsMediumCareDescription() {
        when(plantSpeciesRepository.findByExternalId(10L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(10L)).thenReturn(new PerenualSpeciesDetails(
                10L,
                "   ",
                List.of("Monstera deliciosa", "Monstera borsigiana"),
                "Large foliage plant. Loves bright rooms.",
                null,
                "moderate",
                new PerenualWateringBenchmark(6, 6, "days"),
                List.of("part shade"),
                "medium",
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(10L);

        assertThat(result.getName()).isEqualTo("Monstera deliciosa");
        assertThat(result.getDescription()).contains("moderate care needs").contains("Water about every 6 days");
    }

    @Test
    void importIfMissing_usesModerateKeywordFallbackLightPhraseAndMaxOnlyBenchmark() {
        when(plantSpeciesRepository.findByExternalId(11L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(11L)).thenReturn(new PerenualSpeciesDetails(
                11L,
                "Plant",
                List.of("Plantus testus"),
                "plain description",
                null,
                null,
                new PerenualWateringBenchmark(null, 12, "days"),
                List.of("indirect light"),
                "moderate",
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(11L);

        assertThat(result.getCareProfile().getWaterIntervalDays()).isEqualTo(12);
        assertThat(result.getDescription()).contains("moderate care needs").contains("indirect light");
    }

    @Test
    void importIfMissing_usesMinOnlyBenchmarkAddsDroughtTagAndImportedNotes() {
        when(plantSpeciesRepository.findByExternalId(12L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(12L)).thenReturn(new PerenualSpeciesDetails(
                12L,
                "Plant",
                List.of("Plantus testus"),
                null,
                null,
                null,
                new PerenualWateringBenchmark(15, null, "days"),
                List.of(),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(12L);

        assertThat(result.getCareProfile().getWaterIntervalDays()).isEqualTo(15);
        assertThat(result.getCareProfile().getNotes()).isEqualTo("Imported from Perenual");
        assertThat(result.getTags()).extracting(Tag::getName).contains("засухоустойчивое");
    }

    @Test
    void importIfMissing_derivesPetSafeFastGrowingAndCactusTags() {
        when(plantSpeciesRepository.findByExternalId(13L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(13L)).thenReturn(new PerenualSpeciesDetails(
                13L,
                "Cactus",
                List.of("Cactus testus"),
                "Fast-growing cactus that is non-toxic for pets.",
                null,
                "low",
                null,
                List.of("shade"),
                "easy",
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(13L);

        assertThat(result.getTags()).extracting(Tag::getName)
                .contains("для новичков", "неприхотливое", "кактус", "быстрорастущее", "безопасно для животных", "теневыносливое");
    }

    @Test
    void importIfMissing_derivesMoistureTagFromHighWateringTextWhenBenchmarkMissing() {
        when(plantSpeciesRepository.findByExternalId(14L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(14L)).thenReturn(new PerenualSpeciesDetails(
                14L,
                "Fern",
                List.of("Nephrolepis exaltata"),
                null,
                null,
                "high",
                null,
                List.of("part sun"),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(14L);

        assertThat(result.getTags()).extracting(Tag::getName)
                .contains("влаголюбивое", "полутень");
    }

    @Test
    void importIfMissing_benchmarkHasPriorityOverWateringTextAndMayLeaveWateringTagAbsent() {
        when(plantSpeciesRepository.findByExternalId(15L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(15L)).thenReturn(new PerenualSpeciesDetails(
                15L,
                "Balanced plant",
                List.of("Balanced plantus"),
                null,
                null,
                "high",
                new PerenualWateringBenchmark(7, 9, "days"),
                List.of("full shade"),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(15L);

        assertThat(result.getCareProfile().getWaterIntervalDays()).isEqualTo(8);
        assertThat(result.getTags()).extracting(Tag::getName)
                .contains("теневыносливое")
                .doesNotContain("влаголюбивое", "засухоустойчивое");
    }


    @Test
    void importIfMissing_doesNotAddWateringTagForUnknownWateringTextWhenBenchmarkMissing() {
        when(plantSpeciesRepository.findByExternalId(16L)).thenReturn(Optional.empty());
        when(perenualClient.getSpeciesDetails(16L)).thenReturn(new PerenualSpeciesDetails(
                16L,
                "Neutral plant",
                List.of("Neutralis plantus"),
                null,
                null,
                "average",
                null,
                List.of(),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(16L);

        assertThat(result.getTags()).extracting(Tag::getName)
                .doesNotContain("влаголюбивое", "засухоустойчивое");
    }

    @Test
    void importIfMissing_usesExistingTagInsteadOfCreatingNewOne() {
        Tag existingTag = new Tag();
        existingTag.setId(777L);
        existingTag.setName("яркий свет");

        when(plantSpeciesRepository.findByExternalId(17L)).thenReturn(Optional.empty());
        when(tagRepository.findByNameIgnoreCase("яркий свет")).thenReturn(Optional.of(existingTag));
        when(perenualClient.getSpeciesDetails(17L)).thenReturn(new PerenualSpeciesDetails(
                17L,
                "Sunny plant",
                List.of("Sunny plantus"),
                null,
                null,
                null,
                null,
                List.of("full sun"),
                null,
                null,
                null
        ));

        PlantSpecies result = service.importIfMissing(17L);

        assertThat(result.getTags()).containsExactly(existingTag);
        verify(tagRepository, never()).save(any(Tag.class));
    }

    @Test
    void refreshImageOnceIfNeeded_coversEarlyReturns() {
        assertThat(service.refreshImageOnceIfNeeded(null)).isNull();

        PlantSpecies withImage = new PlantSpecies();
        withImage.setExternalId(18L);
        withImage.setImageUrl(" http://img ");
        assertThat(service.refreshImageOnceIfNeeded(withImage)).isSameAs(withImage);

        PlantSpecies withoutExternalId = new PlantSpecies();
        assertThat(service.refreshImageOnceIfNeeded(withoutExternalId)).isSameAs(withoutExternalId);

        PlantSpecies invalidExternalId = new PlantSpecies();
        invalidExternalId.setExternalId(0L);
        assertThat(service.refreshImageOnceIfNeeded(invalidExternalId)).isSameAs(invalidExternalId);

        PlantSpecies alreadyAttempted = new PlantSpecies();
        alreadyAttempted.setExternalId(18L);
        alreadyAttempted.setImageLookupAttempted(true);
        assertThat(service.refreshImageOnceIfNeeded(alreadyAttempted)).isSameAs(alreadyAttempted);

        verify(perenualClient, never()).getSpeciesDetails(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void refreshImageOnceIfNeeded_savesImageAndMarksAttempted() {
        PlantSpecies species = new PlantSpecies();
        species.setExternalId(19L);

        when(perenualClient.getSpeciesDetails(19L)).thenReturn(new PerenualSpeciesDetails(
                19L,
                "Image plant",
                List.of("Image plantus"),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                " http://new-image ",
                null
        ));

        PlantSpecies result = service.refreshImageOnceIfNeeded(species);

        assertThat(result).isSameAs(species);
        assertThat(result.getImageUrl()).isEqualTo("http://new-image");
        assertThat(result.isImageLookupAttempted()).isTrue();
        verify(plantSpeciesRepository).save(species);
    }

    @Test
    void refreshImageOnceIfNeeded_savesAttemptEvenWhenRemoteImageIsBlank() {
        PlantSpecies species = new PlantSpecies();
        species.setExternalId(20L);

        when(perenualClient.getSpeciesDetails(20L)).thenReturn(new PerenualSpeciesDetails(
                20L,
                "Blank image plant",
                List.of("Blankus plantus"),
                null,
                null,
                null,
                null,
                List.of(),
                null,
                "   ",
                null
        ));

        PlantSpecies result = service.refreshImageOnceIfNeeded(species);

        assertThat(result).isSameAs(species);
        assertThat(result.getImageUrl()).isNull();
        assertThat(result.isImageLookupAttempted()).isTrue();
        verify(plantSpeciesRepository).save(species);
    }

    @Test
    void refreshImageOnceIfNeeded_returnsOriginalWhenPerenualCallFails() {
        PlantSpecies species = new PlantSpecies();
        species.setExternalId(21L);

        when(perenualClient.getSpeciesDetails(21L)).thenThrow(new IllegalStateException("boom"));

        PlantSpecies result = service.refreshImageOnceIfNeeded(species);

        assertThat(result).isSameAs(species);
        assertThat(result.isImageLookupAttempted()).isFalse();
        verify(plantSpeciesRepository, never()).save(any(PlantSpecies.class));
    }

    @Test
    void privateStaticHelpers_coverRemainingBranches() throws Exception {
        Method guessWater = PerenualImportService.class.getDeclaredMethod("guessWaterIntervalDays", PerenualWateringBenchmark.class);
        Method mapLight = PerenualImportService.class.getDeclaredMethod("mapLightLevel", List.class);
        Method buildCardDescription = PerenualImportService.class.getDeclaredMethod("buildCardDescription", PerenualSpeciesDetails.class);
        Method carePhrase = PerenualImportService.class.getDeclaredMethod("carePhrase", String.class);
        Method lightPhrase = PerenualImportService.class.getDeclaredMethod("lightPhrase", List.class);
        Method wateringPhrase = PerenualImportService.class.getDeclaredMethod("wateringPhrase", PerenualWateringBenchmark.class, String.class);
        Method firstSentence = PerenualImportService.class.getDeclaredMethod("firstSentence", String.class, int.class);
        Method normalizeText = PerenualImportService.class.getDeclaredMethod("normalizeText", String.class);
        Method normalizeLower = PerenualImportService.class.getDeclaredMethod("normalizeLower", String.class);
        Method firstScientific = PerenualImportService.class.getDeclaredMethod("firstScientific", List.class);
        Method firstNonBlank = PerenualImportService.class.getDeclaredMethod("firstNonBlank", String[].class);
        Method containsAny = PerenualImportService.class.getDeclaredMethod("containsAny", String.class, String[].class);

        guessWater.setAccessible(true);
        mapLight.setAccessible(true);
        buildCardDescription.setAccessible(true);
        carePhrase.setAccessible(true);
        lightPhrase.setAccessible(true);
        wateringPhrase.setAccessible(true);
        firstSentence.setAccessible(true);
        normalizeText.setAccessible(true);
        normalizeLower.setAccessible(true);
        firstScientific.setAccessible(true);
        firstNonBlank.setAccessible(true);
        containsAny.setAccessible(true);

        assertThat(guessWater.invoke(null, new Object[]{null})).isNull();
        assertThat(guessWater.invoke(null, new PerenualWateringBenchmark(null, null, "days"))).isNull();
        assertThat(guessWater.invoke(null, new PerenualWateringBenchmark(4, null, "days"))).isEqualTo(4);
        assertThat(guessWater.invoke(null, new PerenualWateringBenchmark(null, 9, "days"))).isEqualTo(9);
        assertThat(guessWater.invoke(null, new PerenualWateringBenchmark(4, 8, "days"))).isEqualTo(6);

        assertThat(mapLight.invoke(null, new Object[]{null})).isNull();
        assertThat(mapLight.invoke(null, List.of())).isNull();
        assertThat(mapLight.invoke(null, List.of("full sun"))).isEqualTo("яркий свет");
        assertThat(mapLight.invoke(null, List.of("part shade"))).isEqualTo("полутень");
        assertThat(mapLight.invoke(null, List.of("part sun"))).isEqualTo("полутень");
        assertThat(mapLight.invoke(null, List.of("shade"))).isEqualTo("теневыносливое");
        assertThat(mapLight.invoke(null, List.of("full shade"))).isEqualTo("теневыносливое");
        assertThat(mapLight.invoke(null, List.of("indirect light"))).isNull();
        assertThat(mapLight.invoke(null, List.of("part indirect light"))).isNull();

        assertThat(carePhrase.invoke(null, new Object[]{null})).isNull();
        assertThat(carePhrase.invoke(null, "easy")).isEqualTo("Low-maintenance plant");
        assertThat(carePhrase.invoke(null, "low maintenance")).isEqualTo("Low-maintenance plant");
        assertThat(carePhrase.invoke(null, "beginner friendly")).isEqualTo("Low-maintenance plant");
        assertThat(carePhrase.invoke(null, "moderate")).isEqualTo("Plant with moderate care needs");
        assertThat(carePhrase.invoke(null, "medium")).isEqualTo("Plant with moderate care needs");
        assertThat(carePhrase.invoke(null, "difficult")).isEqualTo("Plant with higher care needs");
        assertThat(carePhrase.invoke(null, "hard")).isEqualTo("Plant with higher care needs");
        assertThat(carePhrase.invoke(null, "high")).isEqualTo("Plant with higher care needs");
        assertThat(carePhrase.invoke(null, "unknown")).isNull();

        assertThat(lightPhrase.invoke(null, new Object[]{null})).isNull();
        assertThat(lightPhrase.invoke(null, List.of())).isNull();
        assertThat(lightPhrase.invoke(null, List.of("full sun"))).isEqualTo("bright light");
        assertThat(lightPhrase.invoke(null, List.of("part shade"))).isEqualTo("partial shade");
        assertThat(lightPhrase.invoke(null, List.of("part sun"))).isEqualTo("partial shade");
        assertThat(lightPhrase.invoke(null, List.of("shade"))).isEqualTo("shade");
        assertThat(lightPhrase.invoke(null, List.of("full shade"))).isEqualTo("shade");
        assertThat(lightPhrase.invoke(null, List.of(" indirect light "))).isEqualTo("indirect light");
        assertThat(lightPhrase.invoke(null, List.of("part indirect light"))).isEqualTo("part indirect light");

        assertThat(wateringPhrase.invoke(null, null, null)).isNull();
        assertThat(wateringPhrase.invoke(null, new PerenualWateringBenchmark(5, 5, "days"), null)).isEqualTo("Water about every 5 days");
        assertThat(wateringPhrase.invoke(null, new PerenualWateringBenchmark(3, 7, "days"), null)).isEqualTo("Water about every 3–7 days");
        assertThat(wateringPhrase.invoke(null, new PerenualWateringBenchmark(null, 7, "days"), " often ")).isEqualTo("Watering: often");
        assertThat(wateringPhrase.invoke(null, new PerenualWateringBenchmark(7, null, "days"), null)).isNull();
        assertThat(wateringPhrase.invoke(null, null, " often ")).isEqualTo("Watering: often");

        assertThat(buildCardDescription.invoke(null, new PerenualSpeciesDetails(
                100L, null, null, null, null, null, null, null, "easy", null, null
        ))).isEqualTo("Low-maintenance plant.");
        assertThat(buildCardDescription.invoke(null, new PerenualSpeciesDetails(
                101L, null, null, null, null, null, null, List.of("shade"), null, null, null
        ))).isEqualTo("Plant that prefers shade.");
        assertThat(buildCardDescription.invoke(null, new PerenualSpeciesDetails(
                102L, null, null, "Decorative plant for home", null, null, null, null, null, null, null
        ))).isEqualTo("Decorative plant for home");

        assertThat(firstSentence.invoke(null, null, 10)).isNull();
        assertThat(firstSentence.invoke(null, "One. Two", 50)).isEqualTo("One.");
        assertThat(firstSentence.invoke(null, "Very long sentence without dot", 10)).isEqualTo("Very long...");

        assertThat(normalizeText.invoke(null, new Object[]{null})).isNull();
        assertThat(normalizeText.invoke(null, "   ")).isNull();
        assertThat(normalizeText.invoke(null, " a   b ")).isEqualTo("a b");

        assertThat(normalizeLower.invoke(null, new Object[]{null})).isNull();
        assertThat(normalizeLower.invoke(null, " HeLLo ")).isEqualTo("hello");

        assertThat(firstScientific.invoke(null, new Object[]{null})).isNull();
        assertThat(firstScientific.invoke(null, List.of())).isNull();
        assertThat(firstScientific.invoke(null, java.util.Arrays.asList(" ", null, " Ficus elastica "))).isEqualTo("Ficus elastica");
        assertThat(firstScientific.invoke(null, java.util.Arrays.asList(" ", null, "\t"))).isNull();

        assertThat(firstNonBlank.invoke(null, (Object) new String[]{null, " ", " Name "})).isEqualTo("Name");
        assertThat(firstNonBlank.invoke(null, (Object) new String[]{null, " ", ""})).isNull();

        assertThat((boolean) containsAny.invoke(null, null, (Object) new String[]{"a"})).isFalse();
        assertThat((boolean) containsAny.invoke(null, "abc", (Object) new String[]{"x", "b"})).isTrue();
        assertThat((boolean) containsAny.invoke(null, "abc", (Object) new String[]{"x", null})).isFalse();
    }
}