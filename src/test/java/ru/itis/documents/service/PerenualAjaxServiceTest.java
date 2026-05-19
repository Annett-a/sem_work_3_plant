package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PerenualAjaxServiceTest {

    @Mock
    PerenualClient perenualClient;
    @Mock
    PerenualImportService perenualImportService;
    @Mock
    PlantSpeciesRepository plantSpeciesRepository;
    @Mock
    CapriciousnessService capriciousnessService;

    @InjectMocks
    PerenualAjaxService service;

    @Test
    void previewByScientificName_throwsBadRequestForBlankName() {
        assertThatThrownBy(() -> service.previewByScientificName("   "))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("Не получилось подготовить запрос");
    }


    @Test
    void previewByScientificName_throwsBadRequestWhenScientificNameNormalizationReturnsBlank() {
        try (org.mockito.MockedStatic<PlantRecognitionImportService> mocked =
                     org.mockito.Mockito.mockStatic(PlantRecognitionImportService.class)) {

            mocked.when(() -> PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus elastica"))
                    .thenReturn("   ");

            assertThatThrownBy(() -> service.previewByScientificName("Ficus elastica"))
                    .isInstanceOf(IntegrationException.class)
                    .hasMessageContaining("Не получилось подготовить запрос");
        }
    }

    @Test
    void previewByScientificName_returnsLocalPreviewWithoutPerenualCall() {
        PlantSpecies species = localSpecies(10L, 77L, "Ficus", "Ficus elastica");
        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        PerenualPreviewView result = service.previewByScientificName("Ficus elastica");

        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.localSpeciesId()).isEqualTo(10L);
        assertThat(result.perenualId()).isEqualTo(77L);
        verify(perenualClient, never()).searchSpecies(anyString());
    }

    @Test
    void previewByScientificName_localMatchWithoutLocalIdFallsBackToRemoteSearch() {
        PlantSpecies species = localSpecies(null, 77L, "Ficus", "Ficus elastica");
        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(2L, "Rubber plant", List.of("Ficus elastica"), "img2")
        ));
        when(plantSpeciesRepository.existsByExternalId(2L)).thenReturn(false);

        PerenualPreviewView result = service.previewByScientificName("Ficus elastica");

        assertThat(result.perenualId()).isEqualTo(2L);
        assertThat(result.alreadyImported()).isFalse();
        assertThat(result.localSpeciesId()).isNull();
        verify(perenualClient).searchSpecies("Ficus elastica");
    }

    @Test
    void previewByScientificName_throwsNotFoundWhenRemoteSearchReturnsNothing() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus")).thenReturn(List.of());

        assertThatThrownBy(() -> service.previewByScientificName("Ficus elastica"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void previewByScientificName_prefersExactRemoteScientificNameAndMarksImported() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(1L, "Some other", List.of("Ficus lyrata"), "img1"),
                new PerenualSpeciesShort(2L, "Rubber plant", List.of("Ficus elastica", "Ficus robusta"), "img2")
        ));
        when(plantSpeciesRepository.existsByExternalId(2L)).thenReturn(true);
        PlantSpecies species = localSpecies(9L, 2L, "Ficus", "Ficus elastica");
        when(plantSpeciesRepository.findByExternalId(2L)).thenReturn(Optional.of(species));

        PerenualPreviewView result = service.previewByScientificName("Ficus elastica");

        assertThat(result.perenualId()).isEqualTo(2L);
        assertThat(result.name()).isEqualTo("Rubber plant");
        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.localSpeciesId()).isEqualTo(9L);
    }

    @Test
    void previewByScientificName_returnsRemotePreviewWithDefaultNameWhenCommonAndScientificBlankAndNotImported() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Unknown plant")).thenReturn(List.of(
                new PerenualSpeciesShort(5L, "   ", Arrays.asList(" ", null), "img5")
        ));
        when(plantSpeciesRepository.existsByExternalId(5L)).thenReturn(false);

        PerenualPreviewView result = service.previewByScientificName("Unknown plant");

        assertThat(result.perenualId()).isEqualTo(5L);
        assertThat(result.name()).isEqualTo("Вид #5");
        assertThat(result.scientificName()).isNull();
        assertThat(result.alreadyImported()).isFalse();
        assertThat(result.localSpeciesId()).isNull();
    }

    @Test
    void previewByScientificName_marksImportedButLeavesLocalIdNullWhenExternalIdExistsWithoutLocalRecord() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(2L, null, List.of("Ficus elastica"), "img2")
        ));
        when(plantSpeciesRepository.existsByExternalId(2L)).thenReturn(true);
        when(plantSpeciesRepository.findByExternalId(2L)).thenReturn(Optional.empty());

        PerenualPreviewView result = service.previewByScientificName("Ficus elastica");

        assertThat(result.perenualId()).isEqualTo(2L);
        assertThat(result.name()).isEqualTo("Ficus elastica");
        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.localSpeciesId()).isNull();
    }

    @Test
    void searchCard_throwsBadRequestForBlankQuery() {
        assertThatThrownBy(() -> service.searchCard("   "))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("Введите название");
    }

    @Test
    void searchCard_throwsBadRequestWhenScientificNormalizationReturnsBlank() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());

        try (org.mockito.MockedStatic<PlantRecognitionImportService> mocked =
                     org.mockito.Mockito.mockStatic(PlantRecognitionImportService.class)) {

            mocked.when(() -> PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus"))
                    .thenReturn("   ");

            assertThatThrownBy(() -> service.searchCard("Ficus"))
                    .isInstanceOf(IntegrationException.class)
                    .hasMessageContaining("Не получилось подготовить запрос");

            verify(plantSpeciesRepository).findAll();
            verify(perenualClient, never()).searchSpecies(anyString());
        }
    }

    @Test
    void searchCard_throwsBadRequestWhenScientificNormalizationReturnsNull() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());

        try (org.mockito.MockedStatic<PlantRecognitionImportService> mocked =
                     org.mockito.Mockito.mockStatic(PlantRecognitionImportService.class)) {

            mocked.when(() -> PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus"))
                    .thenReturn(null);

            assertThatThrownBy(() -> service.searchCard("Ficus"))
                    .isInstanceOf(IntegrationException.class)
                    .hasMessageContaining("Не получилось подготовить запрос");

            verify(plantSpeciesRepository).findAll();
            verify(perenualClient, never()).searchSpecies(anyString());
        }
    }

    @Test
    void searchCard_returnsLocalCardWhenSpeciesFoundLocally() {
        PlantSpecies species = localSpecies(10L, 77L, "Фикус", "Ficus elastica");
        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView("HIGH", "Высокая", null, 70, List.of()));

        PerenualSearchCardView result = service.searchCard("фикус");

        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.localSpeciesId()).isEqualTo(10L);
        assertThat(result.careLevel()).isEqualTo("Высокая");
        verify(perenualClient, never()).searchSpecies(anyString());
    }

    @Test
    void searchCard_findLocalCardSkipsZeroScoreAndUsesTieBreakers() {
        PlantSpecies nonMatch = localSpecies(30L, 1L, "Rose", null);
        PlantSpecies first = localSpecies(11L, 2L, "Fig A", null);
        PlantSpecies second = localSpecies(12L, 3L, "Fir B", null);

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(nonMatch, second, first));
        mockRefreshReturnsSame(first);
        when(capriciousnessService.evaluate(first)).thenReturn(null);

        PerenualSearchCardView result = service.searchCard("fi");

        assertThat(result.localSpeciesId()).isEqualTo(11L);
        assertThat(result.name()).isEqualTo("Fig A");
        verify(perenualClient, never()).searchSpecies(anyString());
    }

    @Test
    void searchCard_searchesRemoteImportsAndReturnsLocalCard() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(3L, "Rubber plant", List.of("Ficus elastica"), "img")
        ));
        PlantSpecies imported = localSpecies(13L, 3L, "Rubber plant", "Ficus elastica");
        when(perenualImportService.importIfMissing(3L)).thenReturn(imported);
        when(capriciousnessService.evaluate(imported)).thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        PerenualSearchCardView result = service.searchCard("Ficus elastica");

        assertThat(result.localSpeciesId()).isEqualTo(13L);
        assertThat(result.perenualId()).isEqualTo(3L);
        verify(perenualImportService).importIfMissing(3L);
    }

    @Test
    void searchCard_usesGenusFallbackAndThrowsNotFoundWhenStillEmpty() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus")).thenReturn(List.of());

        assertThatThrownBy(() -> service.searchCard("Ficus elastica"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    void searchCard_singleWordQueryWithoutResultsDoesNotCallGenusFallbackTwice() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Monstera")).thenReturn(List.of());

        assertThatThrownBy(() -> service.searchCard("Monstera"))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("не найден");

        verify(perenualClient).searchSpecies("Monstera");
        verifyNoMoreInteractions(perenualClient);
    }

    @Test
    void searchCard_importsFirstRemoteResultWhenNoExactScientificMatchExists() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(11L, "First", List.of("Ficus lyrata"), "img1"),
                new PerenualSpeciesShort(22L, "Second", List.of("Monstera deliciosa"), "img2")
        ));

        PlantSpecies imported = localSpecies(13L, 11L, "First", "Ficus lyrata");
        when(perenualImportService.importIfMissing(11L)).thenReturn(imported);
        when(capriciousnessService.evaluate(imported))
                .thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        PerenualSearchCardView result = service.searchCard("Ficus elastica");

        assertThat(result.localSpeciesId()).isEqualTo(13L);
        assertThat(result.perenualId()).isEqualTo(11L);
        verify(perenualImportService).importIfMissing(11L);
    }

    @Test
    void searchCard_localCardHandlesMissingCareProfileExternalIdLatinNameAndCapriciousness() {
        PlantSpecies species = new PlantSpecies();
        species.setId(10L);
        species.setExternalId(null);
        species.setName("Фикус");
        species.setLatinName("   ");
        species.setDescription("desc");
        species.setImageUrl("img");

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(null);

        PerenualSearchCardView result = service.searchCard("фикус");

        assertThat(result.alreadyImported()).isTrue();
        assertThat(result.localSpeciesId()).isEqualTo(10L);
        assertThat(result.perenualId()).isZero();
        assertThat(result.name()).isEqualTo("Фикус");
        assertThat(result.scientificName()).isEqualTo("Фикус");
        assertThat(result.scientificNames()).isEmpty();
        assertThat(result.watering()).isNull();
        assertThat(result.wateringMinDays()).isNull();
        assertThat(result.wateringMaxDays()).isNull();
        assertThat(result.sunlight()).isEmpty();
        assertThat(result.careLevel()).isNull();
    }

    @Test
    void searchCard_localCardWithCareProfileAndBlankLightLevelStillBuildsCard() {
        PlantSpecies species = new PlantSpecies();
        species.setId(15L);
        species.setExternalId(88L);
        species.setName("Plant name");
        species.setLatinName(null);
        species.setDescription("desc");
        species.setImageUrl("img");
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(5);
        careProfile.setLightLevel("   ");
        species.setCareProfile(careProfile);

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView("LOW", "Низкая", null, 20, List.of()));

        PerenualSearchCardView result = service.searchCard("plant");

        assertThat(result.perenualId()).isEqualTo(88L);
        assertThat(result.name()).isEqualTo("Plant name");
        assertThat(result.scientificName()).isEqualTo("Plant name");
        assertThat(result.scientificNames()).isEmpty();
        assertThat(result.watering()).isEqualTo("каждые 5 дн.");
        assertThat(result.wateringMinDays()).isEqualTo(5);
        assertThat(result.wateringMaxDays()).isEqualTo(5);
        assertThat(result.sunlight()).isEmpty();
        assertThat(result.careLevel()).isEqualTo("Низкая");
    }


    @Test
    void searchCard_localCardWithCareProfileAndNullLightLevelStillBuildsCard() {
        PlantSpecies species = new PlantSpecies();
        species.setId(16L);
        species.setExternalId(89L);
        species.setName("Plant null light");
        species.setLatinName(null);
        species.setDescription("desc");
        species.setImageUrl("img");
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(6);
        careProfile.setLightLevel(null);
        species.setCareProfile(careProfile);

        when(plantSpeciesRepository.findAll()).thenReturn(List.of(species));
        mockRefreshReturnsSame(species);
        when(capriciousnessService.evaluate(species)).thenReturn(new CapriciousnessView("MID", "Средняя", null, 50, List.of()));

        PerenualSearchCardView result = service.searchCard("plant");

        assertThat(result.perenualId()).isEqualTo(89L);
        assertThat(result.name()).isEqualTo("Plant null light");
        assertThat(result.scientificName()).isEqualTo("Plant null light");
        assertThat(result.scientificNames()).isEmpty();
        assertThat(result.watering()).isEqualTo("каждые 6 дн.");
        assertThat(result.wateringMinDays()).isEqualTo(6);
        assertThat(result.wateringMaxDays()).isEqualTo(6);
        assertThat(result.sunlight()).isEmpty();
        assertThat(result.careLevel()).isEqualTo("Средняя");
    }

    @Test
    void searchCard_importedLocalCardFallsBackToDefaultPlantNameWhenBothNamesBlank() {
        when(plantSpeciesRepository.findAll()).thenReturn(List.of());
        when(perenualClient.searchSpecies("ZZZ plant")).thenReturn(List.of(
                new PerenualSpeciesShort(7L, "Preview", List.of("ZZZ plant"), "img7")
        ));

        PlantSpecies imported = new PlantSpecies();
        imported.setId(70L);
        imported.setExternalId(null);
        imported.setName("   ");
        imported.setLatinName("   ");
        imported.setDescription("desc");
        imported.setImageUrl("img");

        when(perenualImportService.importIfMissing(7L)).thenReturn(imported);
        when(capriciousnessService.evaluate(imported)).thenReturn(null);

        PerenualSearchCardView result = service.searchCard("ZZZ plant");

        assertThat(result.name()).isEqualTo("Растение");
        assertThat(result.scientificName()).isNull();
        assertThat(result.scientificNames()).isEmpty();
        assertThat(result.perenualId()).isZero();
    }

    @Test
    void importByPerenualId_returnsImportedSpeciesView() {
        PlantSpecies species = localSpecies(13L, 3L, "Rubber plant", "Ficus elastica");
        when(perenualImportService.importIfMissing(3L)).thenReturn(species);

        PerenualImportedSpeciesView result = service.importByPerenualId(3L);

        assertThat(result.perenualId()).isEqualTo(3L);
        assertThat(result.localSpeciesId()).isEqualTo(13L);
    }

    @Test
    void importByPerenualId_rethrowsWhenImportFails() {
        when(perenualImportService.importIfMissing(3L)).thenThrow(new IllegalStateException("boom"));
        assertThatThrownBy(() -> service.importByPerenualId(3L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void scoreLocal_coversExactPrefixContainsAndNoMatchBranches() throws Exception {
        PlantSpecies scientificExact = localSpecies(1L, 1L, "Any", "Ficus elastica Roxb.");
        assertThat(invokeScoreLocal(scientificExact, "ignored", "ficus elastica")).isEqualTo(100);

        PlantSpecies latinExact = localSpecies(2L, 2L, "Any", "Monstera deliciosa");
        assertThat(invokeScoreLocal(latinExact, "monstera deliciosa", null)).isEqualTo(95);

        PlantSpecies nameExact = localSpecies(3L, 3L, "Фикус", "Aloe");
        assertThat(invokeScoreLocal(nameExact, "фикус", null)).isEqualTo(90);

        PlantSpecies latinPrefix = localSpecies(4L, 4L, "Any", "Ficus elastica Roxb.");
        assertThat(invokeScoreLocal(latinPrefix, "ignored", "ficus el")).isEqualTo(85);

        PlantSpecies latinStartsWith = localSpecies(5L, 5L, "Any", "Monstera deliciosa");
        assertThat(invokeScoreLocal(latinStartsWith, "monster", null)).isEqualTo(80);

        PlantSpecies latinContains = localSpecies(6L, 6L, "Any", "Ficus microcarpa Roxb.");
        assertThat(invokeScoreLocal(latinContains, "micro", "micro")).isEqualTo(70);
        assertThat(invokeScoreLocal(latinContains, "carpa ro", null)).isEqualTo(65);

        PlantSpecies nameSpecies = localSpecies(7L, 7L, "Ficus room", "Aloe");
        assertThat(invokeScoreLocal(nameSpecies, "fi", null)).isEqualTo(75);
        assertThat(invokeScoreLocal(nameSpecies, "room", null)).isEqualTo(60);

        PlantSpecies emptySpecies = new PlantSpecies();
        emptySpecies.setName(null);
        emptySpecies.setLatinName(null);
        assertThat(invokeScoreLocal(emptySpecies, "zzz", null)).isZero();
    }


    @Test
    void scoreLocal_skipsNormalizedScientificContainsWhenScientificQueryIsPresentButNotContained() throws Exception {
        PlantSpecies species = localSpecies(8L, 8L, "Ficus room", "Ficus microcarpa Roxb.");

        assertThat(invokeScoreLocal(species, "micro", "ficus elastica")).isEqualTo(65);
    }

    @Test
    void privateHelpers_coverNullTrimAndFallbackBranches() throws Exception {
        assertThat(invokePrivateStatic("firstScientific", new Class[]{List.class}, (Object) null))
                .isNull();
        assertThat(invokePrivateStatic("firstScientific", new Class[]{List.class}, List.of()))
                .isNull();
        assertThat(invokePrivateStatic("firstScientific", new Class[]{List.class}, Arrays.asList(" ", null, " Ficus elastica ")))
                .isEqualTo("Ficus elastica");
        assertThat(invokePrivateStatic("firstScientific", new Class[]{List.class}, List.of(" ", "")))
                .isNull();

        assertThat(invokePrivateStatic("firstNonBlank", new Class[]{String[].class}, (Object) new String[]{null, " ", " Latin "}))
                .isEqualTo("Latin");
        assertThat(invokePrivateStatic("firstNonBlank", new Class[]{String[].class}, (Object) new String[]{null, " ", ""}))
                .isNull();

        assertThat(invokePrivateStatic("normalizeText", new Class[]{String.class}, "  ficus  "))
                .isEqualTo("ficus");
        assertThat(invokePrivateStatic("normalizeText", new Class[]{String.class}, "   "))
                .isNull();
        assertThat(invokePrivateStatic("normalizeText", new Class[]{String.class}, (Object) null))
                .isNull();

        assertThat(invokePrivateStatic("safeLower", new Class[]{String.class}, (Object) null))
                .isEqualTo("");
        assertThat(invokePrivateStatic("safeLower", new Class[]{String.class}, "FiCuS"))
                .isEqualTo("ficus");
    }

    @Test
    void searchWithFallback_handlesEmptyGenusWithoutSecondRemoteCall() throws Exception {
        when(perenualClient.searchSpecies("")).thenReturn(List.of());

        invokePrivateInstance("searchWithFallback", new Class[]{String.class}, "");

        verify(perenualClient).searchSpecies("");
        verifyNoMoreInteractions(perenualClient);
    }

    private PlantSpecies localSpecies(Long id, Long externalId, String name, String latinName) {
        PlantSpecies species = new PlantSpecies();
        species.setId(id);
        species.setExternalId(externalId);
        species.setName(name);
        species.setLatinName(latinName);
        species.setDescription("desc");
        species.setImageUrl("img");
        CareProfile careProfile = new CareProfile();
        careProfile.setWaterIntervalDays(7);
        careProfile.setLightLevel("полутень");
        species.setCareProfile(careProfile);
        return species;
    }

    private void mockRefreshReturnsSame(PlantSpecies species) {
        when(perenualImportService.refreshImageOnceIfNeeded(species)).thenReturn(species);
    }

    private int invokeScoreLocal(PlantSpecies species, String q, String qSci) throws Exception {
        Method method = PerenualAjaxService.class.getDeclaredMethod("scoreLocal", PlantSpecies.class, String.class, String.class);
        method.setAccessible(true);
        return (int) method.invoke(service, species, q, qSci);
    }

    private Object invokePrivateStatic(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = PerenualAjaxService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private Object invokePrivateInstance(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = PerenualAjaxService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(service, args);
    }
}
