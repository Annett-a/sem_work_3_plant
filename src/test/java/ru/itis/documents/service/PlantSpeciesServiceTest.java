package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import ru.itis.documents.domain.entity.CareProfile;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.domain.enums.LightLevel;
import ru.itis.documents.dto.view.CapriciousnessView;
import ru.itis.documents.dto.view.PlantSpeciesView;
import ru.itis.documents.repository.PlantSpeciesRepository;
import ru.itis.documents.repository.TagRepository;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantSpeciesServiceTest {

    @Mock
    PlantSpeciesRepository plantSpeciesRepository;
    @Mock
    CapriciousnessService capriciousnessService;
    @Mock
    TagRepository tagRepository;

    @InjectMocks
    PlantSpeciesService service;

    @Test
    void listTopCapriciousSpecies_normalizesFiltersAndLimitsToHundred() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        when(plantSpeciesRepository.findTopCapriciousSpecies(
                eq("ficus"),
                eq("капризное"),
                eq("яркий свет"),
                eq(5),
                argThat((Pageable p) -> p.getPageNumber() == 0 && p.getPageSize() == 100)
        )).thenReturn(List.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("HIGH", 80));

        List<PlantSpeciesView> result = service.listTopCapriciousSpecies("  Ficus ", LightLevel.BRIGHT, 5, "  КАПРИЗНОЕ ", 200);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Ficus");
    }

    @Test
    void listTopCapriciousSpecies_usesDefaultLimitTwentyAndEmptyStringsForBlankInputs() {
        PlantSpecies species = species(2L, "Aloe", "Aloe vera", 14, "тень");
        when(plantSpeciesRepository.findTopCapriciousSpecies(
                eq(""),
                eq(""),
                eq(""),
                eq(null),
                argThat((Pageable p) -> p.getPageNumber() == 0 && p.getPageSize() == 20)
        )).thenReturn(List.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("LOW", 20));

        List<PlantSpeciesView> result = service.listTopCapriciousSpecies("   ", null, null, "   ", 0);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(2L);
    }

    @Test
    void listCatalog_filtersByTextLightCapriciousnessAndAllSelectedTags() {
        PlantSpecies first = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        first.setTags(Set.of(tag(1L, "декоративно-лиственное"), tag(2L, "тропическое")));
        PlantSpecies second = species(2L, "Aloe", "Aloe vera", 14, "тень");
        second.setTags(Set.of(tag(1L, "декоративно-лиственное")));

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(first, second));
        when(capriciousnessService.evaluate(first)).thenReturn(cap("HIGH", 80));
        when(capriciousnessService.evaluate(second)).thenReturn(cap("LOW", 20));

        List<PlantSpeciesView> result = service.listCatalog(
                "ficus",
                LightLevel.BRIGHT,
                "high",
                List.of(tag(1L, "декоративно-лиственное"), tag(2L, "тропическое"))
        );

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(1L);
    }

    @Test
    void listCatalog_matchesQueryByDescriptionAndSortsByNameCaseInsensitive() {
        PlantSpecies zeta = species(2L, "zeta", "Latin z", 5, "яркий свет");
        zeta.setDescription("Perfect plant for small home office");
        PlantSpecies alpha = species(1L, "Alpha", "Latin a", 7, "яркий свет");
        alpha.setDescription("Good HOME plant with compact size");
        PlantSpecies miss = species(3L, "Beta", "Latin b", 7, "яркий свет");
        miss.setDescription("For greenhouse only");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(zeta, alpha, miss));
        when(capriciousnessService.evaluate(any(PlantSpecies.class))).thenAnswer(inv -> cap("MID", 50));

        List<PlantSpeciesView> result = service.listCatalog(" home ", null, null, null);

        assertThat(result).extracting(PlantSpeciesView::name).containsExactly("Alpha", "zeta");
    }

    @Test
    void listCatalog_ignoresNullAndIdlessSelectedTags() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        species.setTags(Set.of(tag(10L, "тропическое")));
        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        List<PlantSpeciesView> result = service.listCatalog(
                null,
                null,
                null,
                Arrays.asList(null, tag(null, "без id"))
        );

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(1L);
    }

    @Test
    void listCatalog_filtersOutSpeciesWithoutCareWhenLightFilterIsSet() {
        PlantSpecies noCare = new PlantSpecies();
        noCare.setId(1L);
        noCare.setName("No care");
        PlantSpecies withCare = species(2L, "With care", "Latin", 7, "яркий свет");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(noCare, withCare));
        when(capriciousnessService.evaluate(noCare)).thenReturn(cap("LOW", 10));
        when(capriciousnessService.evaluate(withCare)).thenReturn(cap("HIGH", 70));

        List<PlantSpeciesView> result = service.listCatalog(null, LightLevel.BRIGHT, null, null);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(2L);
    }

    @Test
    void listCatalog_returnsEmptyWhenNothingMatchesFilters() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        assertThat(service.listCatalog("x", null, null, null)).isEmpty();
    }

    @Test
    void listSuitableForApartment_delegatesToRepository() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        when(plantSpeciesRepository.findSuitableForApartment("q", "light", 5, "tag", 10)).thenReturn(List.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        assertThat(service.listSuitableForApartment("q", "light", 5, "tag", 10)).hasSize(1);
        verify(plantSpeciesRepository).findSuitableForApartment("q", "light", 5, "tag", 10);
    }

    @Test
    void listSuitableForApartment_returnsEmptyWhenRepositoryReturnsNothing() {
        when(plantSpeciesRepository.findSuitableForApartment(any(), any(), any(), any(), any())).thenReturn(List.of());
        assertThat(service.listSuitableForApartment(null, null, null, null, null)).isEmpty();
    }

    @Test
    void getDetails_returnsMappedViewWithWateringTextFromInterval() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        PlantSpeciesView result = service.getDetails(1L).orElseThrow();

        assertThat(result.care().wateringText()).isEqualTo("7 дн.");
    }

    @Test
    void getDetails_returnsMappedViewWithWateringTextFromNotesPrefix() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", null, "яркий свет");
        species.getCareProfile().setNotes("Watering: weekly • Care level: easy");
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        assertThat(service.getDetails(1L).orElseThrow().care().wateringText()).isEqualTo("weekly");
    }

    @Test
    void getDetails_sortsTagsFiltersNullTagIdsAndKeepsImageAndCapriciousness() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", 7, "яркий свет");
        species.setImageUrl("http://img");
        species.setTags(Set.of(
                tag(2L, "тропическое"),
                tag(null, "без id"),
                tag(1L, "декоративно-лиственное")
        ));
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("HIGH", 88));

        PlantSpeciesView result = service.getDetails(1L).orElseThrow();

        assertThat(result.imageUrl()).isEqualTo("http://img");
        assertThat(result.tags()).containsExactly("без id", "декоративно-лиственное", "тропическое");
        assertThat(result.tagIds()).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result.capriciousness().key()).isEqualTo("HIGH");
    }

    @Test
    void getDetails_returnsViewWithoutCareWhenCareProfileMissing() {
        PlantSpecies species = new PlantSpecies();
        species.setId(1L);
        species.setName("Ficus");
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        assertThat(service.getDetails(1L).orElseThrow().care()).isNull();
    }

    @Test
    void getDetails_returnsNullWateringTextWhenNotesDoNotHaveWateringPrefix() {
        PlantSpecies species = species(1L, "Ficus", "Ficus elastica", null, "яркий свет");
        species.getCareProfile().setNotes("No watering info");
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        assertThat(service.getDetails(1L).orElseThrow().care().wateringText()).isNull();
    }

    @Test
    void getDetails_returnsEmptyWhenSpeciesMissing() {
        when(plantSpeciesRepository.findById(1L)).thenReturn(Optional.empty());
        assertThat(service.getDetails(1L)).isEmpty();
    }


    @Test
    void listTopCapriciousSpecies_usesDefaultLimitTwentyWhenLimitIsNull() {
        PlantSpecies species = species(3L, "Calathea", "Calathea orbifolia", 6, "полутень");
        when(plantSpeciesRepository.findTopCapriciousSpecies(
                eq("calathea"),
                eq(""),
                eq(""),
                eq(null),
                argThat((Pageable p) -> p.getPageNumber() == 0 && p.getPageSize() == 20)
        )).thenReturn(List.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        List<PlantSpeciesView> result = service.listTopCapriciousSpecies(" Calathea ", null, null, null, null);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(3L);
    }

    @Test
    void listCatalog_matchesQueryByLatinNameOnly() {
        PlantSpecies latinMatch = species(4L, "Комнатное растение", "Ficus elastica", 7, "яркий свет");
        latinMatch.setDescription("ordinary description");
        PlantSpecies miss = species(5L, "Алоэ", "Aloe vera", 7, "яркий свет");
        miss.setDescription("ordinary description");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(latinMatch, miss));
        when(capriciousnessService.evaluate(any(PlantSpecies.class))).thenAnswer(inv -> cap("MID", 50));

        List<PlantSpeciesView> result = service.listCatalog(" elastica ", null, null, null);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(4L);
    }

    @Test
    void listCatalog_filtersOutSpeciesWhenCareExistsButLightDoesNotMatch() {
        PlantSpecies wrongLight = species(6L, "Wrong light", "Latin a", 7, "тень");
        PlantSpecies rightLight = species(7L, "Right light", "Latin b", 7, "яркий свет");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(wrongLight, rightLight));
        when(capriciousnessService.evaluate(wrongLight)).thenReturn(cap("LOW", 20));
        when(capriciousnessService.evaluate(rightLight)).thenReturn(cap("HIGH", 80));

        List<PlantSpeciesView> result = service.listCatalog(null, LightLevel.BRIGHT, null, null);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(7L);
    }

    @Test
    void listCatalog_filtersByCapriciousnessAndExcludesNullAndDifferentValues() {
        PlantSpecies nullCap = species(8L, "Null cap", "Latin a", 7, "яркий свет");
        PlantSpecies lowCap = species(9L, "Low cap", "Latin b", 7, "яркий свет");
        PlantSpecies highCap = species(10L, "High cap", "Latin c", 7, "яркий свет");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(nullCap, lowCap, highCap));
        when(capriciousnessService.evaluate(nullCap)).thenReturn(null);
        when(capriciousnessService.evaluate(lowCap)).thenReturn(cap("LOW", 20));
        when(capriciousnessService.evaluate(highCap)).thenReturn(cap("HIGH", 80));

        List<PlantSpeciesView> result = service.listCatalog(null, null, "high", null);

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(10L);
    }

    @Test
    void listCatalog_filtersByAllSelectedTagsAndExcludesSpeciesMissingSomeTags() {
        PlantSpecies allTags = species(11L, "All tags", "Latin a", 7, "яркий свет");
        allTags.setTags(Set.of(tag(1L, "тропическое"), tag(2L, "цветущее")));
        PlantSpecies partialTags = species(12L, "Partial tags", "Latin b", 7, "яркий свет");
        partialTags.setTags(Set.of(tag(1L, "тропическое")));

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(allTags, partialTags));
        when(capriciousnessService.evaluate(allTags)).thenReturn(cap("MID", 50));
        when(capriciousnessService.evaluate(partialTags)).thenReturn(cap("MID", 50));

        List<PlantSpeciesView> result = service.listCatalog(
                null,
                null,
                null,
                List.of(tag(1L, "тропическое"), tag(2L, "цветущее"))
        );

        assertThat(result).singleElement().extracting(PlantSpeciesView::id).isEqualTo(11L);
    }

    @Test
    void getDetails_returnsEmptyTagListsWhenSpeciesTagsAreNull() {
        PlantSpecies species = species(13L, "Ficus", "Ficus elastica", 7, "яркий свет");
        species.setTags(null);
        when(plantSpeciesRepository.findById(13L)).thenReturn(Optional.of(species));
        when(capriciousnessService.evaluate(species)).thenReturn(cap("MID", 50));

        PlantSpeciesView result = service.getDetails(13L).orElseThrow();

        assertThat(result.tags()).isEmpty();
        assertThat(result.tagIds()).isEmpty();
    }

    @Test
    void listCatalog_tagFilterLambda_returnsFalseWhenSelectedTagsPresentButViewTagIdsNull() throws Exception {
        Method method = Arrays.stream(PlantSpeciesService.class.getDeclaredMethods())
                .filter(m -> m.getName().startsWith("lambda$listCatalog$"))
                .filter(m -> Arrays.equals(m.getParameterTypes(), new Class<?>[]{Set.class, PlantSpeciesView.class}))
                .findFirst()
                .orElseThrow();

        method.setAccessible(true);

        PlantSpeciesView view = new PlantSpeciesView(
                99L,
                "Ficus",
                "Ficus elastica",
                null,
                "desc",
                null,
                null,
                List.of(),
                null
        );

        boolean result = (boolean) method.invoke(null, Set.of(1L), view);

        assertThat(result).isFalse();
    }

    @Test
    void getCuratedTagOptions_filtersAndSortsAllowedTags() {
        Tag decorative = tag(1L, "Декоративно-лиственное");
        Tag tropical = tag(2L, "тропическое");
        Tag excluded = tag(3L, "редкое");
        Tag noName = new Tag();

        when(tagRepository.findAll()).thenReturn(Arrays.asList(excluded, decorative, null, noName, tropical));

        List<Tag> result = service.getCuratedTagOptions();

        assertThat(result).extracting(Tag::getName)
                .containsExactly("Декоративно-лиственное", "тропическое");
    }

    @Test
    void getCuratedTagOptions_returnsEmptyListWhenNothingMatches() {
        when(tagRepository.findAll()).thenReturn(List.of(tag(1L, "редкое"), tag(2L, "уличное")));

        List<Tag> result = service.getCuratedTagOptions();

        assertThat(result).isEmpty();
    }

    @Test
    void privateHelpers_coverNormalizeContainsEqualsAndWateringBranches() throws Exception {
        Method normalize = PlantSpeciesService.class.getDeclaredMethod("normalize", String.class);
        Method contains = PlantSpeciesService.class.getDeclaredMethod("containsIgnoreCase", String.class, String.class);
        Method equals = PlantSpeciesService.class.getDeclaredMethod("equalsIgnoreCase", String.class, String.class);
        Method extract = PlantSpeciesService.class.getDeclaredMethod("extractWateringText", CareProfile.class);

        normalize.setAccessible(true);
        contains.setAccessible(true);
        equals.setAccessible(true);
        extract.setAccessible(true);

        assertThat(normalize.invoke(null, new Object[]{null})).isNull();
        assertThat(normalize.invoke(null, "   ")).isNull();
        assertThat(normalize.invoke(null, "  Ficus  ")).isEqualTo("Ficus");

        assertThat((boolean) contains.invoke(null, null, "a")).isFalse();
        assertThat((boolean) contains.invoke(null, "abc", null)).isFalse();
        assertThat((boolean) contains.invoke(null, "Ficus Elastica", "elas")).isTrue();
        assertThat((boolean) contains.invoke(null, "Ficus", "zzz")).isFalse();

        assertThat((boolean) equals.invoke(null, null, "A")).isFalse();
        assertThat((boolean) equals.invoke(null, "A", null)).isFalse();
        assertThat((boolean) equals.invoke(null, "HIGH", "high")).isTrue();
        assertThat((boolean) equals.invoke(null, "HIGH", "low")).isFalse();

        assertThat(extract.invoke(null, new Object[]{null})).isNull();

        CareProfile noNotes = new CareProfile();
        noNotes.setWaterIntervalDays(null);
        noNotes.setNotes(null);
        assertThat(extract.invoke(null, noNotes)).isNull();

        CareProfile noPrefix = new CareProfile();
        noPrefix.setNotes("No watering");
        assertThat(extract.invoke(null, noPrefix)).isNull();

        CareProfile noSeparator = new CareProfile();
        noSeparator.setNotes("Watering: weekly");
        assertThat(extract.invoke(null, noSeparator)).isEqualTo("weekly");
    }

    @Test
    void listRecentImportedSpecies_mapsRepositoryResults() {
        PlantSpecies first = species(21L, "Monstera", "Monstera deliciosa", 5, "полутень");
        first.setExternalId(101L);
        first.setImageUrl("http://img-1");

        PlantSpecies second = species(22L, "Zamioculcas", "Zamioculcas zamiifolia", 10, "тень");
        second.setExternalId(102L);
        second.setImageUrl("http://img-2");

        when(plantSpeciesRepository.findTop12ByExternalIdIsNotNullOrderByCreatedAtDesc())
                .thenReturn(List.of(first, second));

        when(capriciousnessService.evaluate(first)).thenReturn(cap("MID", 50));
        when(capriciousnessService.evaluate(second)).thenReturn(cap("LOW", 20));

        List<PlantSpeciesView> result = service.listRecentImportedSpecies();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PlantSpeciesView::id).containsExactly(21L, 22L);
        assertThat(result).extracting(PlantSpeciesView::name).containsExactly("Monstera", "Zamioculcas");

        verify(plantSpeciesRepository).findTop12ByExternalIdIsNotNullOrderByCreatedAtDesc();
    }

    private CapriciousnessView cap(String key, int score) {
        return new CapriciousnessView(key, key, null, score, List.of());
    }

    private PlantSpecies species(Long id, String name, String latinName, Integer waterDays, String lightLevel) {
        PlantSpecies species = new PlantSpecies();
        species.setId(id);
        species.setName(name);
        species.setLatinName(latinName);
        species.setDescription("desc");
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(waterDays);
        careProfile.setLightLevel(lightLevel);
        species.setCareProfile(careProfile);
        return species;
    }

    private Tag tag(Long id, String name) {
        Tag tag = new Tag();
        tag.setId(id);
        tag.setName(name);
        return tag;
    }
}
