package ru.itis.documents.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itis.documents.domain.entity.Tag;
import ru.itis.documents.domain.enums.LightLevel;
import ru.itis.documents.dto.view.PlantSpeciesView;
import ru.itis.documents.service.PlantSpeciesService;

import java.util.List;

@RestController
@RequestMapping("/api/species")
@RequiredArgsConstructor
public class PlantSpeciesApiController {

    private final PlantSpeciesService plantSpeciesService;

    @GetMapping
    public ResponseEntity<List<PlantSpeciesView>> catalog(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "light", required = false) LightLevel light,
            @RequestParam(value = "cap", required = false) String cap,
            @RequestParam(value = "tags", required = false) List<Tag> tags
    ) {
        return ResponseEntity.ok(
                plantSpeciesService.listCatalog(q, light, cap, tags)
        );
    }
}