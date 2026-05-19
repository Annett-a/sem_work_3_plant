package ru.itis.documents.controller.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.itis.documents.domain.entity.IdentificationCandidate;
import ru.itis.documents.domain.entity.PlantIdentification;
import ru.itis.documents.dto.ApiErrorResponse;
import ru.itis.documents.dto.view.PlantnetCandidateAjaxView;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.PlantIdentificationService;
import ru.itis.documents.service.UserPlantPhotoService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/identify")
@RequiredArgsConstructor
public class IdentifyApiController {

    private final UserPlantPhotoService userPlantPhotoService;
    private final PlantIdentificationService plantIdentificationService;

    @PostMapping(
            value = "/plantnet",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> identifyPlantnet(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestPart("file") MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("FILE_REQUIRED", "Нужно выбрать файл изображения", null));
        }

        String ct = file.getContentType();
        if (ct != null && !(ct.equalsIgnoreCase("image/jpeg")
                || ct.equalsIgnoreCase("image/png")
                || ct.equalsIgnoreCase("image/webp"))) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("BAD_FILE_TYPE", "Разрешены только JPG/PNG/WebP", ct));
        }

        try {
            String username = principal.getUsername();

            Long id = plantIdentificationService.identify(username, file);
            PlantIdentification pi = plantIdentificationService.getDetails(username, id);

            return ResponseEntity.ok(toAjaxViews(pi));
        } catch (IllegalArgumentException ex) {
            log.warn("Plant identification API bad file: user={}, originalFilename={}, contentType={}",
                    principal != null ? principal.getUsername() : "anonymous",
                    file != null ? file.getOriginalFilename() : null,
                    file != null ? file.getContentType() : null,
                    ex);
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("BAD_FILE", ex.getMessage(), null));
        }
    }

    @PostMapping(
            value = "/plantnet/photo/{photoId}",
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> identifyPlantnetSavedPhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long photoId
    ) {
        Long userId = principal.getUser().getId();
        String username = principal.getUsername();

        try {
            var pb = userPlantPhotoService.loadMyPhotoBytes(userId, photoId);

            Long id = plantIdentificationService.identifyFromExistingPhoto(
                    username,
                    pb.storageKey(),
                    pb.originalName(),
                    pb.bytes()
            );
            PlantIdentification pi = plantIdentificationService.getDetails(username, id);

            return ResponseEntity.ok(toAjaxViews(pi));

        } catch (UserPlantPhotoService.PhotoNotFoundException ex) {
            log.warn("Plant identification API photo not found: userId={}, username={}, photoId={}",
                    userId,
                    username,
                    photoId,
                    ex);
            return ResponseEntity.status(404)
                    .body(new ApiErrorResponse("PHOTO_NOT_FOUND", "Фото не найдено", Map.of("photoId", photoId)));

        } catch (IllegalArgumentException ex) {
            log.warn("Plant identification API bad saved photo: userId={}, username={}, photoId={}",
                    userId,
                    username,
                    photoId,
                    ex);
            return ResponseEntity.badRequest()
                    .body(new ApiErrorResponse("BAD_FILE", ex.getMessage(), Map.of("photoId", photoId)));
        }
    }

    private static List<PlantnetCandidateAjaxView> toAjaxViews(PlantIdentification pi) {
        if (pi == null || pi.getCandidates() == null) return List.of();

        return pi.getCandidates().stream()
                .filter(c -> c != null && c.getScientificName() != null)
                .map(IdentifyApiController::toAjaxView)
                .toList();
    }

    private static PlantnetCandidateAjaxView toAjaxView(IdentificationCandidate c) {
        List<String> commonNames = null;
        if (c.getCommonNames() != null && !c.getCommonNames().isBlank()) {
            commonNames = List.of(c.getCommonNames().split("\\s*,\\s*"))
                    .stream()
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toList();
        }

        double score = (c.getScore() == null) ? 0.0d : c.getScore();

        return new PlantnetCandidateAjaxView(
                c.getScientificName(),
                score,
                commonNames
        );
    }
}