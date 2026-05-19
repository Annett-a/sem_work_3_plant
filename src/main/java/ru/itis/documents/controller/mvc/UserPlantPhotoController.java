package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.form.UserPlantPhotoUploadForm;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.UserPlantPhotoService;
import ru.itis.documents.service.UserPlantService;

@Controller
@RequestMapping("/app/plants")
@RequiredArgsConstructor
public class UserPlantPhotoController {

    private static final Logger log = LoggerFactory.getLogger(UserPlantPhotoController.class);
    private final UserPlantService userPlantService;
    private final UserPlantPhotoService userPlantPhotoService;

    @GetMapping("/photos/{photoId}")
    @ResponseBody
    public ResponseEntity<Resource> getPhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long photoId
    ) {
        Long userId = principal.getUser().getId();

        try {
            var pr = userPlantPhotoService.loadMyPhoto(userId, photoId);

            if (pr.resource() == null) {
                return ResponseEntity.notFound().build();
            }

            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            if (pr.contentType() != null && !pr.contentType().isBlank()) {
                try {
                    mediaType = MediaType.parseMediaType(pr.contentType());
                } catch (Exception ex) {
                    log.warn("Invalid photo content type: photoId={}, contentType={}",
                            photoId,
                            pr.contentType(),
                            ex);
                    mediaType = MediaType.APPLICATION_OCTET_STREAM;
                }
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=86400")
                    .body(pr.resource());

        } catch (UserPlantPhotoService.PhotoNotFoundException ex) {
            log.warn("Load photo failed: photo not found, userId={}, photoId={}",
                    userId,
                    photoId,
                    ex);
            return ResponseEntity.notFound().build();
        } catch (IllegalArgumentException ex) {
            log.warn("Load photo failed: invalid photo request, userId={}, photoId={}",
                    userId,
                    photoId,
                    ex);
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            log.error("Failed to load photo: userId={}, photoId={}", userId, photoId, ex);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/{id}/photos")
    public String photoDiary(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model,
            HttpServletResponse response
    ) {
        Long userId = principal.getUser().getId();

        return userPlantService.getMyPlantDetails(userId, id)
                .map(p -> {
                    model.addAttribute("p", p);
                    model.addAttribute("photos", userPlantPhotoService.listMyPlantPhotos(userId, id));
                    return "app/plants/photos";
                })
                .orElseGet(() -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    model.addAttribute("message", "Растение не найдено");
                    return "app/plants/not_found";
                });
    }

    @PostMapping("/{id}/photos/{photoId}/delete")
    public String deletePhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @PathVariable Long photoId,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        try {
            userPlantPhotoService.deleteMyPhoto(userId, id, photoId);
            redirectAttributes.addFlashAttribute("msg", "Фото удалено");
        } catch (UserPlantPhotoService.PhotoNotFoundException ex) {
            log.warn("Delete photo failed: photo not found, userId={}, plantId={}, photoId={}",
                    userId,
                    id,
                    photoId,
                    ex);
            redirectAttributes.addFlashAttribute("msg", "Фото не найдено");
        } catch (UserPlantPhotoService.UserPlantNotFoundException ex) {
            log.warn("Delete photo failed: plant not found, userId={}, plantId={}, photoId={}",
                    userId,
                    id,
                    photoId,
                    ex);
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
            return "redirect:/app/plants";
        }
        return "redirect:/app/plants/" + id + "/photos";
    }

    @PostMapping("/{id}/photos")
    public String uploadPhoto(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("photoForm") UserPlantPhotoUploadForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.photoForm", bindingResult);
            redirectAttributes.addFlashAttribute("photoForm", form);
            return "redirect:/app/plants/" + id;
        }

        try {
            userPlantPhotoService.addPhoto(userId, id, form.getPhoto());
            redirectAttributes.addFlashAttribute("msg", "Фото загружено");
            return "redirect:/app/plants/" + id;

        } catch (IllegalArgumentException ex) {
            log.warn("Upload photo failed: invalid file, userId={}, plantId={}, originalFilename={}",
                    userId,
                    id,
                    form.getPhoto() != null ? form.getPhoto().getOriginalFilename() : null,
                    ex);
            bindingResult.addError(new FieldError("photoForm", "photo", ex.getMessage()));
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.photoForm", bindingResult);
            redirectAttributes.addFlashAttribute("photoForm", form);
            return "redirect:/app/plants/" + id;

        } catch (UserPlantPhotoService.UserPlantNotFoundException ex) {
            log.warn("Upload photo failed: plant not found, userId={}, plantId={}, originalFilename={}",
                    userId,
                    id,
                    form.getPhoto() != null ? form.getPhoto().getOriginalFilename() : null,
                    ex);
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
            return "redirect:/app/plants";
        }
    }
}