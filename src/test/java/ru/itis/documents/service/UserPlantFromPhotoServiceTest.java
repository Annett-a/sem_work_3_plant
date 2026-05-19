package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.itis.documents.form.UserPlantByPhotoForm;
import ru.itis.documents.form.UserPlantCreateForm;
import ru.itis.documents.service.PlantIdentificationService;
import ru.itis.documents.service.PlantRecognitionImportService;
import ru.itis.documents.service.UserPlantFromPhotoService;
import ru.itis.documents.service.UserPlantService;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPlantFromPhotoServiceTest {

    @Mock
    PlantIdentificationService plantIdentificationService;
    @Mock
    PlantRecognitionImportService plantRecognitionImportService;
    @Mock
    UserPlantService userPlantService;

    @InjectMocks
    UserPlantFromPhotoService service;

    @Test
    void createFromPhoto_usesExistingSpeciesIdWhenItWasProvided() {
        UserPlantByPhotoForm form = form();
        form.setSpeciesId(55L);
        when(userPlantService.create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(UserPlantCreateForm.class)))
                .thenReturn(999L);

        Long result = service.createFromPhoto("user@example.com", 7L, 15L, form);

        assertThat(result).isEqualTo(999L);
        verify(plantIdentificationService).selectScientificName("user@example.com", 15L, "Ficus elastica");
        ArgumentCaptor<UserPlantCreateForm> captor = ArgumentCaptor.forClass(UserPlantCreateForm.class);
        verify(userPlantService).create(org.mockito.ArgumentMatchers.eq(7L), captor.capture());
        assertThat(captor.getValue().getSpeciesId()).isEqualTo(55L);
    }

    @Test
    void createFromPhoto_importsSpeciesWhenLocalSpeciesIdMissing() {
        UserPlantByPhotoForm form = form();
        form.setSpeciesId(null);
        form.setPerenualId(123L);
        when(plantRecognitionImportService.importSelectedCandidateToCatalog("user@example.com", 15L, 123L)).thenReturn(42L);
        when(userPlantService.create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(UserPlantCreateForm.class)))
                .thenReturn(100L);

        Long result = service.createFromPhoto("user@example.com", 7L, 15L, form);

        assertThat(result).isEqualTo(100L);
        verify(plantRecognitionImportService).importSelectedCandidateToCatalog("user@example.com", 15L, 123L);
    }

    @Test
    void createFromPhoto_importsSpeciesWhenSpeciesIdIsZeroAndCopiesAllFields() {
        UserPlantByPhotoForm form = form();
        form.setSpeciesId(0L);
        form.setPerenualId(123L);

        form.setRoomId(5L);

        when(plantRecognitionImportService.importSelectedCandidateToCatalog("user@example.com", 15L, 123L))
                .thenReturn(42L);
        when(userPlantService.create(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any(UserPlantCreateForm.class)))
                .thenReturn(100L);

        Long result = service.createFromPhoto("user@example.com", 7L, 15L, form);

        assertThat(result).isEqualTo(100L);
        verify(plantIdentificationService).selectScientificName("user@example.com", 15L, "Ficus elastica");
        verify(plantRecognitionImportService).importSelectedCandidateToCatalog("user@example.com", 15L, 123L);

        ArgumentCaptor<UserPlantCreateForm> captor = ArgumentCaptor.forClass(UserPlantCreateForm.class);
        verify(userPlantService).create(org.mockito.ArgumentMatchers.eq(7L), captor.capture());

        UserPlantCreateForm created = captor.getValue();
        assertThat(created.getSpeciesId()).isEqualTo(42L);
        assertThat(created.getRoomId()).isEqualTo(5L);
        assertThat(created.getNickname()).isEqualTo("Фикус");
        assertThat(created.getPurchaseDate()).isEqualTo(LocalDate.of(2026, 3, 20));
        assertThat(created.getNotes()).isEqualTo("новый");
    }

    private UserPlantByPhotoForm form() {
        UserPlantByPhotoForm form = new UserPlantByPhotoForm();
        form.setSelectedScientificName("Ficus elastica");
        form.setNickname("Фикус");
        form.setPurchaseDate(LocalDate.of(2026, 3, 20));
        form.setNotes("новый");
        return form;
    }
}
