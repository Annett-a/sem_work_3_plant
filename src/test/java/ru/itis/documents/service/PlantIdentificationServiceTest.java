package ru.itis.documents.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.domain.entity.AppUser;
import ru.itis.documents.domain.entity.IdentificationCandidate;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.domain.enums.PlantIdentificationStatus;
import ru.itis.documents.exception.IntegrationException;
import ru.itis.documents.integration.plantnet.PlantNetClient;
import ru.itis.documents.repository.AppUserRepository;
import ru.itis.documents.repository.PlantIdentificationRepository;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

import org.mockito.MockedStatic;

import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlantIdentificationServiceTest {

    @Mock
    AppUserRepository appUserRepository;
    @Mock
    PlantIdentificationRepository identificationRepository;
    @Mock
    FileStorageService fileStorageService;
    @Mock
    PlantNetClient plantNetClient;

    PlantIdentificationService service;
    AtomicLong ids;

    @BeforeEach
    void init() {
        service = new PlantIdentificationService(
                appUserRepository,
                identificationRepository,
                fileStorageService,
                plantNetClient,
                new ObjectMapper()
        );
        ids = new AtomicLong(100);
        lenient().when(identificationRepository.save(any(PlantIdentification.class))).thenAnswer(inv -> {
            PlantIdentification pi = inv.getArgument(0);
            if (pi.getId() == null) {
                pi.setId(ids.getAndIncrement());
            }
            return pi;
        });
    }

    @Test
    void identify_reusesCachedCompletedIdentification() throws Exception {
        AppUser user = user(1L);
        MockMultipartFile photo = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{1, 2, 3});
        PlantIdentification cached = completedIdentification(55L, user, "stored.jpg", "Ficus elastica");
        cached.setBestMatch("Ficus elastica");
        cached.setBestMatchScore(0.99);
        cached.setPlantnetRemainingRequests(4);
        cached.setRawResponseJson("{}");
        cached.addCandidate(candidate("Ficus elastica", "rubber plant", 0.99));
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.of(cached));

        when(fileStorageService.saveUserUpload(1L, photo)).thenReturn("uploads/1/cached-copy.jpg");

        Long resultId = service.identify("user@example.com", photo);

        assertThat(resultId).isNotNull();
        verify(fileStorageService).saveUserUpload(1L, photo);
        verify(plantNetClient, never()).identify(any(byte[].class), any());
        verify(identificationRepository).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.COMPLETED
                        && "uploads/1/cached-copy.jpg".equals(pi.getSourcePhotoPath())
                        && "Ficus elastica".equals(pi.getSelectedScientificName())
        ));
    }

    @Test
    void identify_savesPhotoCallsPlantNetAndStoresCompletedResult() throws Exception {
        AppUser user = user(1L);
        MockMultipartFile photo = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(fileStorageService.saveUserUpload(1L, photo)).thenReturn("uploads/1/plant.jpg");
        when(plantNetClient.identify(photo.getBytes(), "plant.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                "Ficus elastica",
                List.of(candidateDto("Ficus elastica", List.of("rubber plant"), 0.99)),
                5,
                null,
                "{json}"
        ));

        Long resultId = service.identify("user@example.com", photo);

        assertThat(resultId).isNotNull();
        ArgumentCaptor<PlantIdentification> captor = ArgumentCaptor.forClass(PlantIdentification.class);
        verify(identificationRepository, atLeast(2)).save(captor.capture());
        PlantIdentification last = captor.getValue();
        assertThat(last.getStatus()).isEqualTo(PlantIdentificationStatus.COMPLETED);
        assertThat(last.getSourcePhotoPath()).isEqualTo("uploads/1/plant.jpg");
        assertThat(last.getSelectedScientificName()).isEqualTo("Ficus elastica");
        assertThat(last.getBestMatchScore()).isEqualTo(0.99);
        assertThat(last.getCandidates()).hasSize(1);
    }

    @Test
    void identify_completesWithoutSelectedScientificNameWhenPlantNetReturnsNoCandidates() throws Exception {
        AppUser user = user(1L);
        MockMultipartFile photo = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{4, 5, 6});
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(fileStorageService.saveUserUpload(1L, photo)).thenReturn("uploads/1/plant.jpg");
        when(plantNetClient.identify(photo.getBytes(), "plant.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                "Unknown",
                List.of(),
                2,
                null,
                "{}"
        ));

        Long resultId = service.identify("user@example.com", photo);

        assertThat(resultId).isNotNull();
        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.COMPLETED
                        && pi.getCandidates().isEmpty()
                        && pi.getSelectedScientificName() == null
                        && pi.getBestMatchScore() == null
                        && "Unknown".equals(pi.getBestMatch())));
    }

    @Test
    void identify_marksFailedAndRethrowsIntegrationException() {
        AppUser user = user(1L);
        MockMultipartFile photo = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(fileStorageService.saveUserUpload(1L, photo)).thenReturn("uploads/1/plant.jpg");
        IntegrationException ex = new IntegrationException("PLANTNET_UNAVAILABLE", "service down", null, 503);
        when(plantNetClient.identify(any(byte[].class), eq("plant.jpg"))).thenThrow(ex);

        assertThatThrownBy(() -> service.identify("user@example.com", photo))
                .isInstanceOf(IntegrationException.class)
                .hasMessageContaining("service down");

        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.FAILED && "service down".equals(pi.getErrorMessage())));
    }

    @Test
    void identify_marksFailedAndReturnsIdForUnexpectedException() {
        AppUser user = user(1L);
        MockMultipartFile photo = new MockMultipartFile("photo", "plant.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(fileStorageService.saveUserUpload(1L, photo)).thenReturn("uploads/1/plant.jpg");
        when(plantNetClient.identify(any(byte[].class), eq("plant.jpg")))
                .thenThrow(new IllegalStateException("boom"));

        Long resultId = service.identify("user@example.com", photo);

        assertThat(resultId).isNotNull();
        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.FAILED && "boom".equals(pi.getErrorMessage())));
    }

    @Test
    void identify_throwsWhenMultipartCannotBeRead() throws Exception {
        MultipartFile photo = org.mockito.Mockito.mock(MultipartFile.class);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(1L)));
        when(photo.getBytes()).thenThrow(new java.io.IOException("boom"));

        assertThatThrownBy(() -> service.identify("user@example.com", photo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void identify_throwsWhenMultipartIsNull() {
        when(appUserRepository.findByEmailIgnoreCase("user@example.com"))
                .thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.identify("user@example.com", (MultipartFile) null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Не удалось прочитать файл");

        verify(fileStorageService, never()).saveUserUpload(any(), any());
        verify(plantNetClient, never()).identify(any(byte[].class), any());
    }

    @Test
    void identifyFromExistingPhoto_reusesCacheAndKeepsProvidedSourcePath() {
        AppUser user = user(1L);
        PlantIdentification cached = completedIdentification(55L, user, "stored.jpg", "Ficus elastica");
        cached.setBestMatch("Ficus elastica");
        cached.setBestMatchScore(0.99);
        cached.setPlantnetRemainingRequests(4);
        cached.setRawResponseJson("{}");
        cached.addCandidate(candidate("Ficus elastica", "rubber plant", 0.99));
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.of(cached));

        Long id = service.identifyFromExistingPhoto("user@example.com", "photos/saved.jpg", "saved.jpg", new byte[]{9, 8, 7});

        assertThat(id).isNotNull();
        verify(plantNetClient, never()).identify(any(byte[].class), any());
        verify(identificationRepository).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.COMPLETED
                        && "photos/saved.jpg".equals(pi.getSourcePhotoPath())
                        && "Ficus elastica".equals(pi.getSelectedScientificName())
                        && pi.getCandidates().size() == 1));
    }

    @Test
    void identifyFromExistingPhoto_completesAndPersistsCandidatesWithoutCache() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(plantNetClient.identify(new byte[]{1, 2, 3}, "saved.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                "Monstera deliciosa",
                List.of(candidateDto("Monstera deliciosa", List.of("monstera"), 0.88)),
                3,
                null,
                "{}"
        ));

        Long id = service.identifyFromExistingPhoto("user@example.com", "photos/saved.jpg", "saved.jpg", new byte[]{1, 2, 3});

        assertThat(id).isNotNull();
        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.COMPLETED
                        && pi.getCandidates().size() == 1
                        && "Monstera deliciosa".equals(pi.getSelectedScientificName())
                        && Double.valueOf(0.88).equals(pi.getBestMatchScore())));
    }

    @Test
    void identifyFromExistingPhoto_completesWithoutSelectedScientificNameWhenPlantNetReturnsNoCandidates() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(plantNetClient.identify(new byte[]{1, 2}, "saved.jpg")).thenReturn(new PlantNetClient.IdentifyResult(
                "Unknown",
                List.of(),
                1,
                null,
                "{}"
        ));

        Long id = service.identifyFromExistingPhoto("user@example.com", "photos/saved.jpg", "saved.jpg", new byte[]{1, 2});

        assertThat(id).isNotNull();
        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.COMPLETED
                        && pi.getCandidates().isEmpty()
                        && pi.getSelectedScientificName() == null
                        && pi.getBestMatchScore() == null));
    }

    @Test
    void identifyFromExistingPhoto_throwsForNullBytes() {
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.identifyFromExistingPhoto("user@example.com", "x", "x.jpg", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пустой файл");
    }

    @Test
    void identifyFromExistingPhoto_throwsForEmptyBytes() {
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user(1L)));

        assertThatThrownBy(() -> service.identifyFromExistingPhoto("user@example.com", "x", "x.jpg", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пустой файл");
    }

    @Test
    void identifyFromExistingPhoto_marksFailedAndRethrowsIntegrationException() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(plantNetClient.identify(new byte[]{1}, "saved.jpg"))
                .thenThrow(new IntegrationException("PLANTNET_UNAVAILABLE", "down", null, 503));

        assertThatThrownBy(() -> service.identifyFromExistingPhoto("user@example.com", "photos/saved.jpg", "saved.jpg", new byte[]{1}))
                .isInstanceOf(IntegrationException.class);

        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.FAILED && "down".equals(pi.getErrorMessage())));
    }

    @Test
    void identifyFromExistingPhoto_marksFailedAndReturnsIdForUnexpectedException() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findFirstByUser_IdAndPhotoHashAndStatusOrderByCreatedAtDesc(
                eq(1L), any(String.class), eq(PlantIdentificationStatus.COMPLETED)))
                .thenReturn(Optional.empty());
        when(plantNetClient.identify(new byte[]{1}, "saved.jpg")).thenThrow(new IllegalStateException("boom"));

        Long id = service.identifyFromExistingPhoto("user@example.com", "photos/saved.jpg", "saved.jpg", new byte[]{1});

        assertThat(id).isNotNull();
        verify(identificationRepository, atLeast(2)).save(org.mockito.ArgumentMatchers.argThat(pi ->
                pi.getStatus() == PlantIdentificationStatus.FAILED && "boom".equals(pi.getErrorMessage())));
    }

    @Test
    void history_getDetails_selectScientificName_andPhotoPathFor_workAsExpected() {
        AppUser user = user(1L);
        PlantIdentification identification = completedIdentification(55L, user, "stored.jpg", "Ficus elastica");
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findHistory(1L)).thenReturn(List.of(identification));
        when(identificationRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.of(identification));

        assertThat(service.history("user@example.com")).containsExactly(identification);
        assertThat(service.getDetails("user@example.com", 55L)).isSameAs(identification);
        service.selectScientificName("user@example.com", 55L, "Monstera deliciosa");
        assertThat(identification.getSelectedScientificName()).isEqualTo("Monstera deliciosa");
        assertThat(service.photoPathFor("user@example.com", 55L)).isEqualTo("stored.jpg");
    }

    @Test
    void history_returnsEmptyListWhenUserHasNoIdentifications() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findHistory(1L)).thenReturn(List.of());
        assertThat(service.history("user@example.com")).isEmpty();
    }

    @Test
    void selectScientificName_throwsWhenIdentificationMissing() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.selectScientificName("user@example.com", 55L, "x"))
                .isInstanceOf(PlantIdentificationNotFoundException.class)
                .hasMessageContaining("не найдено");
    }

    @Test
    void photoPathFor_throwsWhenIdentificationMissing() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.photoPathFor("user@example.com", 55L))
                .isInstanceOf(PlantIdentificationNotFoundException.class);
    }

    @Test
    void getDetails_throwsWhenIdentificationMissing() {
        AppUser user = user(1L);
        when(appUserRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(user));
        when(identificationRepository.findByIdAndUser_Id(55L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getDetails("user@example.com", 55L))
                .isInstanceOf(PlantIdentificationNotFoundException.class);
    }

    @Test
    void resolveUser_throwsWhenUserMissing() {
        when(appUserRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.history("missing@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Пользователь");
    }

    @Test
    void sha256Hex_wrapsNoSuchAlgorithmExceptionIntoIllegalStateException() throws Exception {
        Method method = PlantIdentificationService.class.getDeclaredMethod("sha256Hex", byte[].class);
        method.setAccessible(true);

        try (MockedStatic<MessageDigest> mocked = org.mockito.Mockito.mockStatic(MessageDigest.class)) {
            mocked.when(() -> MessageDigest.getInstance("SHA-256"))
                    .thenThrow(new NoSuchAlgorithmException("SHA-256 missing"));

            InvocationTargetException ex = org.junit.jupiter.api.Assertions.assertThrows(
                    InvocationTargetException.class,
                    () -> method.invoke(null, (Object) new byte[]{1, 2, 3})
            );

            assertThat(ex.getCause()).isInstanceOf(IllegalStateException.class);
            assertThat(ex.getCause()).hasMessageContaining("SHA-256 not available");
            assertThat(ex.getCause().getCause()).isInstanceOf(NoSuchAlgorithmException.class);
            assertThat(ex.getCause().getCause()).hasMessageContaining("SHA-256 missing");
        }
    }

    @Test
    void sha256Hex_returnsStableExpectedHashForDifferentInputs() throws Exception {
        Method method = PlantIdentificationService.class.getDeclaredMethod("sha256Hex", byte[].class);
        method.setAccessible(true);

        assertThat((String) method.invoke(null, (Object) new byte[]{1, 2, 3}))
                .isEqualTo("039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
        assertThat((String) method.invoke(null, (Object) new byte[0]))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    private AppUser user(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setEmail("user@example.com");
        return user;
    }

    private PlantIdentification completedIdentification(Long id, AppUser user, String path, String selectedName) {
        PlantIdentification identification = new PlantIdentification();
        identification.setId(id);
        identification.setUser(user);
        identification.setCreatedAt(Instant.now());
        identification.setStatus(PlantIdentificationStatus.COMPLETED);
        identification.setSourcePhotoPath(path);
        identification.setSelectedScientificName(selectedName);
        identification.setCandidates(new java.util.ArrayList<>());
        return identification;
    }

    private IdentificationCandidate candidate(String sci, String common, double score) {
        IdentificationCandidate candidate = new IdentificationCandidate();
        candidate.setScientificName(sci);
        candidate.setCommonNames(common);
        candidate.setScore(score);
        return candidate;
    }

    private PlantNetClient.Candidate candidateDto(String sci, List<String> common, double score) {
        return new PlantNetClient.Candidate(sci, common, score);
    }
}
