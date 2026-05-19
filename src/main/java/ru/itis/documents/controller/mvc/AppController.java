package ru.itis.documents.controller.mvc;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.exception.ExternalApiUnavailableException;
import ru.itis.documents.service.PerenualImportService;
import ru.itis.documents.service.PlantSpeciesService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AppController {

    private final PlantSpeciesService plantSpeciesService;
    private final PerenualImportService perenualImportService;

    @GetMapping("/app")
    public String appHome() {
        return "app/index";
    }

    @GetMapping("/admin")
    public String adminHome(Model model) {
        model.addAttribute("recentImportedSpecies", plantSpeciesService.listRecentImportedSpecies());
        return "admin/index";
    }

    @PostMapping("/admin/import")
    public String adminImportFromPerenual(
            @RequestParam("perenualId") String perenualIdRaw,
            RedirectAttributes redirectAttributes
    ) {
        if (!StringUtils.hasText(perenualIdRaw)) {
            redirectAttributes.addFlashAttribute("error", "Введите корректный Perenual ID");
            return "redirect:/admin";
        }

        long perenualId;
        try {
            perenualId = Long.parseLong(perenualIdRaw.trim());
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("error", "Введите корректный Perenual ID");
            return "redirect:/admin";
        }

        if (perenualId <= 0) {
            redirectAttributes.addFlashAttribute("error", "Введите корректный Perenual ID");
            return "redirect:/admin";
        }

        try {
            Long localId = perenualImportService.importIfMissing(perenualId).getId();
            redirectAttributes.addFlashAttribute("msg", "Вид успешно импортирован");
            return "redirect:/app/species/" + localId;
        } catch (ExternalApiUnavailableException e) {
            log.warn("Admin import unavailable: perenualId={} msg={}", perenualId, e.getUserMessage(), e);
            redirectAttributes.addFlashAttribute("error", e.getUserMessage());
            return "redirect:/admin";
        } catch (RuntimeException e) {
            log.error("Admin import failed: perenualId={}", perenualId, e);
            redirectAttributes.addFlashAttribute("error", "Не удалось импортировать вид");
            return "redirect:/admin";
        }
    }
}