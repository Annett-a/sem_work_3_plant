package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.dto.view.UserPlantDetailsView;
import ru.itis.documents.form.UserPlantCreateForm;
import ru.itis.documents.form.UserPlantPhotoUploadForm;
import ru.itis.documents.form.UserPlantUpdateForm;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.UserPlantPhotoService;
import ru.itis.documents.service.UserPlantService;

@Controller
@RequestMapping("/app/plants")
@RequiredArgsConstructor
public class UserPlantController {

    private static final Logger log = LoggerFactory.getLogger(UserPlantController.class);
    private final UserPlantService userPlantService;
    private final UserPlantPhotoService userPlantPhotoService;

    @GetMapping
    public String list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model
    ) {
        Long userId = principal.getUser().getId();
        model.addAttribute("plants", userPlantService.listMyPlants(userId));
        return "app/plants/list";
    }

    @GetMapping("/new")
    public String createPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserPlantCreateForm());
        }
        model.addAttribute("speciesOptions", userPlantService.speciesOptions());
        model.addAttribute("roomOptions", userPlantService.roomOptions(userId));
        return "app/plants/create";
    }

    @PostMapping
    public String create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @ModelAttribute("form") UserPlantCreateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/new";
        }

        try {
            Long id = userPlantService.create(userId, form);
            redirectAttributes.addFlashAttribute("msg", "Растение добавлено");
            return "redirect:/app/plants/" + id;
        } catch (UserPlantService.SpeciesNotFoundException ex) {
            log.warn("Create plant failed: species not found, userId={}, speciesId={}",
                    userId,
                    form.getSpeciesId(),
                    ex);
            bindingResult.addError(new FieldError("form", "speciesId", ex.getMessage()));
        } catch (UserPlantService.RoomNotFoundException ex) {
            log.warn("Create plant failed: room not found, userId={}, roomId={}",
                    userId,
                    form.getRoomId(),
                    ex);
            bindingResult.addError(new FieldError("form", "roomId", ex.getMessage()));
        }

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/app/plants/new";
    }

    @GetMapping("/{id}")
    public String details(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model,
            HttpServletResponse response
    ) {
        Long userId = principal.getUser().getId();

        return userPlantService.getMyPlantDetails(userId, id)
                .map(p -> {
                    model.addAttribute("p", p);

                    if (!model.containsAttribute("photoForm")) {
                        model.addAttribute("photoForm", new UserPlantPhotoUploadForm());
                    }

                    model.addAttribute("photos", userPlantPhotoService.listMyPlantPhotos(userId, id));

                    return "app/plants/details";
                })
                .orElseGet(() -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    model.addAttribute("message", "Растение не найдено");
                    return "app/plants/not_found";
                });
    }

    @GetMapping("/{id}/edit")
    public String editPage(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model,
            HttpServletResponse response
    ) {
        Long userId = principal.getUser().getId();

        if (!model.containsAttribute("form")) {
            UserPlantDetailsView p = userPlantService.getMyPlantDetails(userId, id).orElse(null);
            if (p == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                model.addAttribute("message", "Растение не найдено");
                return "app/plants/not_found";
            }

            UserPlantUpdateForm form = new UserPlantUpdateForm();
            form.setNickname(p.getNickname());
            form.setSpeciesId(p.getSpeciesId());
            form.setRoomId(p.getRoomId());
            form.setPurchaseDate(p.getPurchaseDate());
            form.setNotes(p.getNotes());
            model.addAttribute("form", form);
        }

        model.addAttribute("id", id);
        model.addAttribute("speciesOptions", userPlantService.speciesOptions());
        model.addAttribute("roomOptions", userPlantService.roomOptions(principal.getUser().getId()));
        return "app/plants/edit";
    }

    @PostMapping("/{id}")
    public String update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            @Valid @ModelAttribute("form") UserPlantUpdateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/" + id + "/edit";
        }

        try {
            userPlantService.update(userId, id, form);
            redirectAttributes.addFlashAttribute("msg", "Изменения сохранены");
            return "redirect:/app/plants/" + id;
        } catch (UserPlantService.UserPlantNotFoundException ex) {
            log.warn("Update plant failed: plant not found, userId={}, plantId={}",
                    userId,
                    id,
                    ex);
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
            return "redirect:/app/plants";
        } catch (UserPlantService.SpeciesNotFoundException ex) {
            log.warn("Update plant failed: species not found, userId={}, plantId={}, speciesId={}",
                    userId,
                    id,
                    form.getSpeciesId(),
                    ex);
            bindingResult.addError(new FieldError("form", "speciesId", ex.getMessage()));
        } catch (UserPlantService.RoomNotFoundException ex) {
            log.warn("Update plant failed: room not found, userId={}, plantId={}, roomId={}",
                    userId,
                    id,
                    form.getRoomId(),
                    ex);
            bindingResult.addError(new FieldError("form", "roomId", ex.getMessage()));
        }

        redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
        redirectAttributes.addFlashAttribute("form", form);
        return "redirect:/app/plants/" + id + "/edit";
    }

    @GetMapping("/{id}/delete")
    public String deleteConfirm(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            Model model,
            HttpServletResponse response
    ) {
        Long userId = principal.getUser().getId();

        return userPlantService.getMyPlantDetails(userId, id)
                .map(p -> {
                    model.addAttribute("p", p);
                    return "app/plants/delete";
                })
                .orElseGet(() -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    model.addAttribute("message", "Растение не найдено");
                    return "app/plants/not_found";
                });
    }

    @PostMapping("/{id}/delete")
    public String delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        try {
            userPlantService.delete(userId, id);
            redirectAttributes.addFlashAttribute("msg", "Растение удалено");
        } catch (UserPlantService.UserPlantNotFoundException ex) {
            log.warn("Delete plant failed: plant not found, userId={}, plantId={}",
                    userId,
                    id,
                    ex);
            redirectAttributes.addFlashAttribute("msg", ex.getMessage());
        }

        return "redirect:/app/plants";
    }
}