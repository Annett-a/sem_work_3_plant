package ru.itis.documents.controller.mvc;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.domain.enums.LightLevel;
import ru.itis.documents.dto.view.PlantSpeciesView;
import ru.itis.documents.dto.view.TagFilterOptionView;
import ru.itis.documents.service.PerenualImportService;
import ru.itis.documents.service.PlantSpeciesService;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/app/species")
@RequiredArgsConstructor
public class PlantSpeciesController {

    private final PlantSpeciesService plantSpeciesService;
    private final PerenualImportService perenualImportService;

    @GetMapping
    public String catalog(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "light", required = false) LightLevel light,
            @RequestParam(value = "cap", required = false) String cap,
            @RequestParam(value = "tags", required = false) List<Tag> tags,
            Model model
    ) {
        List<PlantSpeciesView> species = plantSpeciesService.listCatalog(q, light, cap, tags);
        model.addAttribute("species", species);
        model.addAttribute("q", q);
        model.addAttribute("light", light);
        model.addAttribute("selectedLight", light == null ? null : light.ruLabel());
        model.addAttribute("cap", cap);

        Set<Long> selectedTagIds = (tags == null) ? Set.of() : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(Tag::getId)
                .collect(Collectors.toSet());

        model.addAttribute("tagOptions", plantSpeciesService.getCuratedTagOptions().stream()
                .map(t -> new TagFilterOptionView(
                        t.getId(),
                        t.getName(),
                        t.getId() != null && selectedTagIds.contains(t.getId())
                ))
                .toList());

        String tagsRaw = (tags == null || tags.isEmpty())
                ? ""
                : tags.stream()
                .filter(t -> t != null && t.getId() != null)
                .map(t -> String.valueOf(t.getId()))
                .collect(Collectors.joining(","));
        model.addAttribute("tags", tagsRaw);

        return "app/species/catalog";
    }

    @GetMapping("/suitable")
    public String suitable(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "light", required = false) LightLevel light,
            @RequestParam(value = "maxWaterInterval", required = false) Integer maxWaterInterval,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "limit", required = false) Integer limit,
            Model model
    ) {
        String lightNorm = (light == null) ? null : light.ruLabel();

        List<PlantSpeciesView> species = plantSpeciesService.listSuitableForApartment(
                q,
                lightNorm,
                maxWaterInterval,
                tag,
                limit
        );

        model.addAttribute("species", species);
        model.addAttribute("q", q);
        model.addAttribute("light", light);
        model.addAttribute("selectedLight", light == null ? null : light.ruLabel());
        model.addAttribute("maxWaterInterval", maxWaterInterval);
        model.addAttribute("tag", tag);
        model.addAttribute("limit", limit);

        String selectedTag = (tag == null) ? null : tag.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("tagOptions", plantSpeciesService.getCuratedTagOptions().stream()
                .map(t -> new TagFilterOptionView(
                        t.getId(),
                        t.getName(),
                        t.getName() != null && selectedTag != null
                                && t.getName().trim().toLowerCase(Locale.ROOT).equals(selectedTag)
                ))
                .toList());

        return "app/species/suitable";
    }


    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model, HttpServletResponse response) {
        return plantSpeciesService.getDetails(id)
                .map(s -> {
                    model.addAttribute("s", s);
                    return "app/species/details";
                })
                .orElseGet(() -> {
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                    model.addAttribute("message", "Вид растения не найден");
                    return "app/species/not_found";
                });
    }

    @PostMapping("/import")
    public String importFromPerenual(
            @RequestParam("perenualId") String perenualIdRaw,
            RedirectAttributes redirectAttributes
    ) {
        if (!StringUtils.hasText(perenualIdRaw)) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        long perenualId;
        try {
            perenualId = Long.parseLong(perenualIdRaw.trim());
        } catch (NumberFormatException e) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        if (perenualId <= 0) {
            redirectAttributes.addFlashAttribute("msg", "Некорректный ID для импорта");
            return "redirect:/app/species";
        }

        Long localId = perenualImportService.importIfMissing(perenualId).getId();
        redirectAttributes.addFlashAttribute("msg", "Вид импортирован");
        return "redirect:/app/species/" + localId; // PRG
    }

    @GetMapping("/top-capricious")
    public String topCapricious(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "light", required = false) LightLevel light,
            @RequestParam(value = "maxWaterInterval", required = false) Integer maxWaterInterval,
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "limit", required = false) Integer limit,
            Model model
    ) {
        List<PlantSpeciesView> species = plantSpeciesService.listTopCapriciousSpecies(
                q, light, maxWaterInterval, tag, limit
        );

        model.addAttribute("species", species);
        model.addAttribute("q", q);
        model.addAttribute("light", light);
        model.addAttribute("selectedLight", light == null ? null : light.ruLabel());
        model.addAttribute("maxWaterInterval", maxWaterInterval);
        model.addAttribute("tag", tag);
        model.addAttribute("limit", limit);

        String selectedTag = (tag == null) ? null : tag.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("tagOptions", plantSpeciesService.getCuratedTagOptions().stream()
                .map(t -> new TagFilterOptionView(
                        t.getId(),
                        t.getName(),
                        t.getName() != null && selectedTag != null
                                && t.getName().trim().toLowerCase(Locale.ROOT).equals(selectedTag)
                ))
                .toList());

        return "app/species/top_capricious";
    }

}