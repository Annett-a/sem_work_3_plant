package ru.itis.documents.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.domain.entity.Photo;
import ru.itis.documents.domain.entity.UserPlant;
import ru.itis.documents.dto.view.PhotoItemView;
import ru.itis.documents.repository.PhotoRepository;
import ru.itis.documents.repository.UserPlantRepository;

import java.io.InputStream;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserPlantPhotoService {

    private static final Logger log = LoggerFactory.getLogger(UserPlantPhotoService.class);
    private final UserPlantRepository userPlantRepository;
    private final PhotoRepository photoRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public List<PhotoItemView> listMyPlantPhotos(Long userId, Long plantId) {
        ensureMyPlant(userId, plantId);

        return photoRepository.findAllByUserPlant_IdOrderByUploadedAtDesc(plantId).stream()
                .map(p -> new PhotoItemView(p.getId(), p.getOriginalName(), p.getUploadedAt()))
                .toList();
    }

    @Transactional
    public void addPhoto(Long userId, Long plantId, MultipartFile file) {
        UserPlant plant = ensureMyPlant(userId, plantId);

        String storageKey = fileStorageService.saveUserUpload(userId, file);

        Photo photo = new Photo();
        photo.setUserPlant(plant);
        photo.setStorageKey(storageKey);
        photo.setOriginalName(file.getOriginalFilename());
        photo.setContentType(file.getContentType());

        photoRepository.save(photo);
    }

    @Transactional(readOnly = true)
    public PhotoResource loadMyPhoto(Long userId, Long photoId) {
        Photo p = photoRepository.findByIdAndUserPlant_User_Id(photoId, userId)
                .orElseThrow(() -> new PhotoNotFoundException("Фото не найдено"));

        Resource resource = fileStorageService.load(p.getStorageKey());
        return new PhotoResource(resource, p.getContentType());
    }

    @Transactional
    public void deleteMyPhoto(Long userId, Long plantId, Long photoId) {
        ensureMyPlant(userId, plantId);

        Photo p = photoRepository.findByIdAndUserPlant_IdAndUserPlant_User_Id(photoId, plantId, userId)
                .orElseThrow(() -> new PhotoNotFoundException("Фото не найдено"));

        fileStorageService.delete(p.getStorageKey());

        photoRepository.delete(p);
    }

    private UserPlant ensureMyPlant(Long userId, Long plantId) {
        return userPlantRepository.findByIdAndUser_Id(plantId, userId)
                .orElseThrow(() -> new UserPlantNotFoundException("Растение не найдено"));
    }

    public record PhotoResource(Resource resource, String contentType) {
    }

    public static class UserPlantNotFoundException extends RuntimeException {
        public UserPlantNotFoundException(String message) {
            super(message);
        }
    }

    public static class PhotoNotFoundException extends RuntimeException {
        public PhotoNotFoundException(String message) {
            super(message);
        }
    }

    public record PhotoBytes(byte[] bytes, String originalName, String storageKey) {
    }

    @Transactional(readOnly = true)
    public PhotoBytes loadMyPhotoBytes(Long userId, Long photoId) {
        Photo p = photoRepository.findByIdAndUserPlant_User_Id(photoId, userId)
                .orElseThrow(() -> new PhotoNotFoundException("Фото не найдено"));

        try (InputStream in = fileStorageService.load(p.getStorageKey()).getInputStream()) {
            byte[] bytes = in.readAllBytes();
            String name = (p.getOriginalName() == null || p.getOriginalName().isBlank())
                    ? "image.jpg"
                    : p.getOriginalName();
            return new PhotoBytes(bytes, name, p.getStorageKey());
        } catch (Exception e) {
            log.error("Failed to read photo bytes: userId={}, photoId={}, storageKey={}",
                    userId,
                    photoId,
                    p.getStorageKey(),
                    e);
            throw new IllegalArgumentException("Не удалось прочитать файл изображения", e);
        }
    }
}