package ru.itis.documents.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import ru.itis.documents.domain.entity.Photo;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.dto.view.PhotoItemView;
import ru.itis.documents.repository.PhotoRepository;
import ru.itis.documents.repository.UserPlantRepository;
import ru.itis.documents.service.FileStorageService;
import ru.itis.documents.service.UserPlantPhotoService;

import static org.mockito.Mockito.doThrow;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserPlantPhotoServiceTest {

    @Mock
    UserPlantRepository userPlantRepository;
    @Mock
    PhotoRepository photoRepository;
    @Mock
    FileStorageService fileStorageService;
    @InjectMocks
    UserPlantPhotoService service;

    @Test
    void listMyPlantPhotos_checksOwnershipAndMapsViews() {
        UserPlant plant = plant(10L);
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");
        photo.setUploadedAt(OffsetDateTime.parse("2026-03-25T10:00:00Z"));
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findAllByUserPlant_IdOrderByUploadedAtDesc(10L)).thenReturn(List.of(photo));

        List<PhotoItemView> result = service.listMyPlantPhotos(1L, 10L);

        assertThat(result).containsExactly(new PhotoItemView(1L, "orig.jpg", OffsetDateTime.parse("2026-03-25T10:00:00Z")));
    }

    @Test
    void listMyPlantPhotos_throwsWhenPlantMissing() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.listMyPlantPhotos(1L, 10L))
                .isInstanceOf(UserPlantPhotoService.UserPlantNotFoundException.class);
    }

    @Test
    void addPhoto_savesStorageKeyAndPhotoEntity() {
        UserPlant plant = plant(10L);
        MockMultipartFile file = new MockMultipartFile("photo", "orig.jpg", "image/jpeg", new byte[]{1});
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(fileStorageService.saveUserUpload(1L, file)).thenReturn("uploads/1/file.jpg");

        service.addPhoto(1L, 10L, file);

        verify(photoRepository).save(org.mockito.ArgumentMatchers.argThat(p ->
                p.getUserPlant() == plant &&
                        "uploads/1/file.jpg".equals(p.getStorageKey()) &&
                        "orig.jpg".equals(p.getOriginalName()) &&
                        "image/jpeg".equals(p.getContentType())));
    }

    @Test
    void addPhoto_throwsWhenPlantMissing() {
        MockMultipartFile file = new MockMultipartFile("photo", "orig.jpg", "image/jpeg", new byte[]{1});
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addPhoto(1L, 10L, file))
                .isInstanceOf(UserPlantPhotoService.UserPlantNotFoundException.class);
    }

    @Test
    void addPhoto_doesNotSavePhotoWhenStorageFails() {
        UserPlant plant = plant(10L);
        MockMultipartFile file = new MockMultipartFile("photo", "orig.jpg", "image/jpeg", new byte[]{1});
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(fileStorageService.saveUserUpload(1L, file)).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.addPhoto(1L, 10L, file)).isInstanceOf(IllegalStateException.class);
        verify(photoRepository, never()).save(any());
    }

    @Test
    void loadMyPhoto_returnsResourceAndContentType() {
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.of(photo));
        ByteArrayResource resource = new ByteArrayResource(new byte[]{1, 2, 3});
        when(fileStorageService.load("stored.jpg")).thenReturn(resource);

        UserPlantPhotoService.PhotoResource result = service.loadMyPhoto(1L, 1L);

        assertThat(result.resource()).isSameAs(resource);
        assertThat(result.contentType()).isEqualTo("image/jpeg");
    }

    @Test
    void loadMyPhoto_throwsWhenPhotoMissing() {
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadMyPhoto(1L, 1L))
                .isInstanceOf(UserPlantPhotoService.PhotoNotFoundException.class);
    }

    @Test
    void deleteMyPhoto_deletesFileAndEntity() {
        UserPlant plant = plant(10L);
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findByIdAndUserPlant_IdAndUserPlant_User_Id(1L, 10L, 1L)).thenReturn(Optional.of(photo));

        service.deleteMyPhoto(1L, 10L, 1L);

        verify(fileStorageService).delete("stored.jpg");
        verify(photoRepository).delete(photo);
    }

    @Test
    void deleteMyPhoto_throwsWhenPhotoMissing() {
        UserPlant plant = plant(10L);
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findByIdAndUserPlant_IdAndUserPlant_User_Id(1L, 10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMyPhoto(1L, 10L, 1L))
                .isInstanceOf(UserPlantPhotoService.PhotoNotFoundException.class);
    }

    @Test
    void deleteMyPhoto_throwsWhenPlantMissingBeforePhotoLookup() {
        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteMyPhoto(1L, 10L, 1L))
                .isInstanceOf(UserPlantPhotoService.UserPlantNotFoundException.class);
    }

    @Test
    void loadMyPhotoBytes_readsBytesAndUsesFallbackFileName() {
        Photo photo = photo(1L, "stored.jpg", "   ", "image/jpeg");
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.of(photo));
        when(fileStorageService.load("stored.jpg")).thenReturn(new ByteArrayResource(new byte[]{4, 5, 6}));

        UserPlantPhotoService.PhotoBytes result = service.loadMyPhotoBytes(1L, 1L);

        assertThat(result.bytes()).containsExactly(4, 5, 6);
        assertThat(result.originalName()).isEqualTo("image.jpg");
        assertThat(result.storageKey()).isEqualTo("stored.jpg");
    }

    @Test
    void loadMyPhotoBytes_throwsWhenPhotoMissing() {
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.loadMyPhotoBytes(1L, 1L))
                .isInstanceOf(UserPlantPhotoService.PhotoNotFoundException.class);
    }

    @Test
    void loadMyPhotoBytes_throwsWrappedExceptionWhenResourceCannotBeRead() throws IOException {
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");
        org.springframework.core.io.Resource resource = org.mockito.Mockito.mock(org.springframework.core.io.Resource.class);
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.of(photo));
        when(fileStorageService.load("stored.jpg")).thenReturn(resource);
        when(resource.getInputStream()).thenThrow(new IOException("boom"));

        assertThatThrownBy(() -> service.loadMyPhotoBytes(1L, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("прочитать файл");
    }

    @Test
    void loadMyPhotoBytes_keepsOriginalNameWhenItIsPresent() {
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.of(photo));
        when(fileStorageService.load("stored.jpg")).thenReturn(new ByteArrayResource(new byte[]{7, 8}));

        UserPlantPhotoService.PhotoBytes result = service.loadMyPhotoBytes(1L, 1L);

        assertThat(result.bytes()).containsExactly(7, 8);
        assertThat(result.originalName()).isEqualTo("orig.jpg");
        assertThat(result.storageKey()).isEqualTo("stored.jpg");
    }

    @Test
    void deleteMyPhoto_doesNotDeleteEntityWhenFileDeletionFails() {
        UserPlant plant = plant(10L);
        Photo photo = photo(1L, "stored.jpg", "orig.jpg", "image/jpeg");

        when(userPlantRepository.findByIdAndUser_Id(10L, 1L)).thenReturn(Optional.of(plant));
        when(photoRepository.findByIdAndUserPlant_IdAndUserPlant_User_Id(1L, 10L, 1L)).thenReturn(Optional.of(photo));
        doThrow(new IllegalStateException("boom")).when(fileStorageService).delete("stored.jpg");

        assertThatThrownBy(() -> service.deleteMyPhoto(1L, 10L, 1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");

        verify(photoRepository, never()).delete(any());
    }

    @Test
    void loadMyPhotoBytes_usesFallbackFileNameWhenOriginalNameIsNull() {
        Photo photo = photo(1L, "stored.jpg", null, "image/jpeg");
        when(photoRepository.findByIdAndUserPlant_User_Id(1L, 1L)).thenReturn(Optional.of(photo));
        when(fileStorageService.load("stored.jpg")).thenReturn(new ByteArrayResource(new byte[]{9, 8}));

        UserPlantPhotoService.PhotoBytes result = service.loadMyPhotoBytes(1L, 1L);

        assertThat(result.bytes()).containsExactly(9, 8);
        assertThat(result.originalName()).isEqualTo("image.jpg");
        assertThat(result.storageKey()).isEqualTo("stored.jpg");
    }

    private UserPlant plant(Long id) {
        UserPlant plant = new UserPlant();
        plant.setId(id);
        return plant;
    }

    private Photo photo(Long id, String storageKey, String originalName, String contentType) {
        Photo photo = new Photo();
        photo.setId(id);
        photo.setStorageKey(storageKey);
        photo.setOriginalName(originalName);
        photo.setContentType(contentType);
        return photo;
    }
}
