package ru.itis.documents.controller.mvc;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.form.CareEventCreateForm;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.CareEventService;

@Controller
@RequiredArgsConstructor
@RequestMapping("/app/plants/{plantId}/events")
public class CareEventController {

    private final CareEventService careEventService;

    @GetMapping
    public String journal(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long plantId,
            Model model
    ) {
        Long userId = principal.getUser().getId();

        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new CareEventCreateForm());
        }

        model.addAttribute("plantId", plantId);
        model.addAttribute("events", careEventService.listMyPlantEvents(userId, plantId));
        return "app/plants/events";
    }

    @PostMapping("/water")
    public String addWatering(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @PathVariable Long plantId,
            @Valid @ModelAttribute("form") CareEventCreateForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes
    ) {
        Long userId = principal.getUser().getId();

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/app/plants/" + plantId + "/events";
        }

        careEventService.addWatering(userId, plantId, form.getComment());
        redirectAttributes.addFlashAttribute("msg", "Полив добавлен, план обновлён");
        return "redirect:/app/plants/" + plantId + "/events";
    }
}