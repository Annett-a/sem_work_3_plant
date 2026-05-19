package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.domain.entity.PlantSpecies;
import ru.itis.documents.integration.perenual.PerenualClient;
import ru.itis.documents.integration.perenual.PerenualSpeciesShort;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.PlantIdentificationRepository;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantRecognitionImportServiceTest {

    @Mock
    AppUserRepository appUserRepository;

    @Mock
    PlantIdentificationRepository plantIdentificationRepository;

    @Mock
    PerenualClient perenualClient;

    @Mock
    PerenualImportService perenualImportService;

    @InjectMocks
    PlantRecognitionImportService service;

    @Test
    void importSelectedCandidateToCatalog_twoArgOverload_importsUsingSelectedScientificName() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(11L, "rubber", List.of("Ficus elastica"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(66L);
        when(perenualImportService.importIfMissing(11L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L)).isEqualTo(66L);

        verify(perenualClient).searchSpecies("Ficus elastica");
        verify(perenualImportService).importIfMissing(11L);
    }

    @Test
    void importSelectedCandidateToCatalog_usesProvidedPerenualIdWhenPresent() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));

        PlantSpecies species = new PlantSpecies();
        species.setId(44L);
        when(perenualImportService.importIfMissing(123L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, 123L)).isEqualTo(44L);

        verify(perenualImportService).importIfMissing(123L);
        verify(perenualClient, never()).searchSpecies("Ficus elastica");
    }

    @Test
    void importSelectedCandidateToCatalog_ignoresNonPositivePerenualIdAndUsesSelectedScientificName() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(12L, "rubber", List.of("Ficus elastica"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(77L);
        when(perenualImportService.importIfMissing(12L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, 0L)).isEqualTo(77L);

        verify(perenualClient).searchSpecies("Ficus elastica");
        verify(perenualImportService).importIfMissing(12L);
        verify(perenualImportService, never()).importIfMissing(0L);
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenUserMissing() {
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пользователь");
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenIdentificationMissing() {
        AppUser user = user();

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(PlantIdentificationNotFoundException.class)
                .hasMessageContaining("Распознавание");
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenScientificNameNotChosen() {
        AppUser user = user();
        PlantIdentification identification = identification("   ");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("выбрать кандидата");
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenScientificNameIsNull() {
        AppUser user = user();
        PlantIdentification identification = identification(null);

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("выбрать кандидата");
    }


    @Test
    void importSelectedCandidateToCatalog_throwsWhenNormalizedQueryIsBlank() {
        AppUser user = user();
        PlantIdentification identification = identification(",,,");
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Не получилось подготовить запрос в Perenual");
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenNormalizationReturnsBlank() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));

        try (org.mockito.MockedStatic<PlantRecognitionImportService> mocked =
                     org.mockito.Mockito.mockStatic(PlantRecognitionImportService.class, org.mockito.Mockito.CALLS_REAL_METHODS)) {

            mocked.when(() -> PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus elastica"))
                    .thenReturn("   ");

            assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Не получилось подготовить запрос в Perenual");
        }
    }


    @Test
    void importSelectedCandidateToCatalog_importsExactRemoteMatch() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica (Roxb.)");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(10L, "other", List.of("Ficus lyrata"), null),
                new PerenualSpeciesShort(11L, "rubber", List.of("Ficus elastica"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(66L);
        when(perenualImportService.importIfMissing(11L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, null)).isEqualTo(66L);

        verify(perenualImportService).importIfMissing(11L);
    }

    @Test
    void importSelectedCandidateToCatalog_skipsNullScientificNamesAndStillFindsExactMatch() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(10L, "other", java.util.Arrays.asList((String) null, "Ficus lyrata"), null),
                new PerenualSpeciesShort(11L, "rubber", List.of("Ficus elastica"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(67L);
        when(perenualImportService.importIfMissing(11L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, null)).isEqualTo(67L);

        verify(perenualImportService).importIfMissing(11L);
    }


    @Test
    void importSelectedCandidateToCatalog_usesFirstResultWhenExactScientificNameNotFound() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of(
                new PerenualSpeciesShort(21L, "first", List.of("Ficus lyrata"), null),
                new PerenualSpeciesShort(22L, "second", List.of("Ficus benghalensis"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(88L);
        when(perenualImportService.importIfMissing(21L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, null)).isEqualTo(88L);

        verify(perenualImportService).importIfMissing(21L);
    }

    @Test
    void importSelectedCandidateToCatalog_usesGenusFallbackWhenExactSearchEmpty() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus")).thenReturn(List.of(
                new PerenualSpeciesShort(12L, "rubber", List.of("Ficus elastica"), null)
        ));

        PlantSpecies species = new PlantSpecies();
        species.setId(77L);
        when(perenualImportService.importIfMissing(12L)).thenReturn(species);

        assertThat(service.importSelectedCandidateToCatalog("user@example.com", 15L, null)).isEqualTo(77L);

        verify(perenualClient).searchSpecies("Ficus elastica");
        verify(perenualClient).searchSpecies("Ficus");
        verify(perenualImportService).importIfMissing(12L);
    }

    @Test
    void importSelectedCandidateToCatalog_throwsWhenPerenualReturnsNothingAfterGenusFallback() {
        AppUser user = user();
        PlantIdentification identification = identification("Ficus elastica");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Ficus elastica")).thenReturn(List.of());
        when(perenualClient.searchSpecies("Ficus")).thenReturn(List.of());

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Perenual не нашёл вид по запросу: Ficus elastica")
                .hasMessageContaining("также пробовали: Ficus");
    }

    @Test
    void importSelectedCandidateToCatalog_doesNotTryGenusFallbackForSingleWordQuery() {
        AppUser user = user();
        PlantIdentification identification = identification("Monstera");

        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(plantIdentificationRepository.findByIdAndUser_Id(15L, 1L)).thenReturn(Optional.of(identification));
        when(perenualClient.searchSpecies("Monstera")).thenReturn(List.of());

        assertThatThrownBy(() -> service.importSelectedCandidateToCatalog("user@example.com", 15L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Perenual не нашёл вид по запросу: Monstera")
                .hasMessageNotContaining("также пробовали");

        verify(perenualClient).searchSpecies("Monstera");
        verifyNoMoreInteractions(perenualClient);
    }

    @Test
    void searchWithFallback_privateHelper_handlesEmptyGenusWithoutSecondCall() throws Exception {
        when(perenualClient.searchSpecies("")).thenReturn(List.of());

        Method method = PlantRecognitionImportService.class.getDeclaredMethod("searchWithFallback", String.class);
        method.setAccessible(true);
        method.invoke(service, "");

        verify(perenualClient).searchSpecies("");
        verifyNoMoreInteractions(perenualClient);
    }

    @Test
    void normalizeScientificNameForSearch_handlesNullBlankAndCommonCases() {
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch(null)).isNull();
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("   ")).isNull();
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus elastica Roxb."))
                .isEqualTo("Ficus elastica");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus elastica (Roxb.)"))
                .isEqualTo("Ficus elastica");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Rosa x alba something"))
                .isEqualTo("Rosa x alba");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Rosa × alba something"))
                .isEqualTo("Rosa × alba");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Monstera"))
                .isEqualTo("Monstera");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch("Ficus elastica,,,"))
                .isEqualTo("Ficus elastica");
        assertThat(PlantRecognitionImportService.normalizeScientificNameForSearch(",,,")).isNull();
    }

    private AppUser user() {
        AppUser user = new AppUser();
        user.setId(1L);
        user.setEmail("user@example.com");
        return user;
    }

    private PlantIdentification identification(String selectedScientificName) {
        PlantIdentification identification = new PlantIdentification();
        identification.setId(15L);
        identification.setSelectedScientificName(selectedScientificName);
        return identification;
    }
}