package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.exception.PlantIdentificationNotFoundException;
import ru.itis.documents.form.PlantIdentificationForm;
import ru.itis.documents.form.UserPlantByPhotoForm;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.PlantIdentificationService;
import ru.itis.documents.service.UserPlantFromPhotoService;
import ru.itis.documents.service.UserPlantService;

@Controller
@RequestMapping("/app/plants")
@RequiredArgsConstructor
public class UserPlantFromPhotoController {

    private static final Logger log = LoggerFactory.getLogger(UserPlantFromPhotoController.class);
    private final PlantIdentificationService plantIdentificationService;
    private final UserPlantFromPhotoService userPlantFromPhotoService;
    private final UserPlantService userPlantService;

    @GetMapping("/new/photo")
    public String createByPhotoUploadPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new PlantIdentificationForm());
        }
        return "app/plants/create_photo_upload";
    }

    @PostMapping("/new/photo")
    public String createByPhotoUpload(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("form") PlantIdentificationForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/new/photo";
        }

        try {
            Long identificationId = plantIdentificationService.identify(principal.getUsername(), form.getPhoto());
            return "redirect:/app/plants/new/photo/" + identificationId;
        } catch (Exception e) {
            log.error("Plant identification by photo failed: username={}",
                    principal != null ? principal.getUsername() : "anonymous", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/new/photo";
        }
    }

    @GetMapping("/new/photo/{identId}")
    public String createByPhotoSelectPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long identId,
            Model model
    ) {
        var item = plantIdentificationService.getDetails(principal.getUsername(), identId);
        model.addAttribute("item", item);

        Long userId = principal.getUser().getId();
        model.addAttribute("roomOptions", userPlantService.roomOptions(userId));

        if (!model.containsAttribute("plantForm")) {
            UserPlantByPhotoForm plantForm = new UserPlantByPhotoForm();
            String nickname = (item.getBestMatch() == null || item.getBestMatch().isBlank())
                    ? "Моё растение"
                    : item.getBestMatch();
            plantForm.setNickname(nickname);
            plantForm.setSelectedScientificName(item.getSelectedScientificName());

            model.addAttribute("plantForm", plantForm);
        }

        return "app/plants/create_photo_select";
    }

    @PostMapping("/new/photo/{identId}/create")
    public String createByPhotoFinish(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long identId,
            @Valid @ModelAttribute("plantForm") UserPlantByPhotoForm plantForm,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "org.springframework.validation.BindingResult.plantForm", bindingResult);
            redirectAttributes.addFlashAttribute("plantForm", plantForm);
            return "redirect:/app/plants/new/photo/" + identId;
        }

        try {
            Long userId = principal.getUser().getId();

            Long plantId = userPlantFromPhotoService.createFromPhoto(
                    principal.getUsername(),
                    userId,
                    identId,
                    plantForm
            );

            redirectAttributes.addFlashAttribute("msg", "Растение добавлено по фото");
            return "redirect:/app/plants/" + plantId;

        } catch (PlantIdentificationNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("Create plant from photo failed: username={}, identId={}",
                    principal != null ? principal.getUsername() : "anonymous",
                    identId,
                    e);
            redirectAttributes.addFlashAttribute("plantForm", plantForm);
            redirectAttributes.addFlashAttribute("importError", e.getMessage());
            return "redirect:/app/plants/new/photo/" + identId;
        }
    }
}