package ru.itis.documents.controller.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.itis.documents.dto.ApiErrorResponse;
import ru.itis.documents.dto.view.PerenualImportedSpeciesView;
import ru.itis.documents.dto.view.PerenualPreviewView;
import ru.itis.documents.dto.view.PerenualSearchCardView;
import ru.itis.documents.service.PerenualAjaxService;

@RestController
@RequestMapping("/api/perenual")
@RequiredArgsConstructor
public class PerenualAjaxController {

    private final PerenualAjaxService perenualAjaxService;

    @GetMapping(value = "/preview", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> preview(@RequestParam("scientificName") String scientificName) {
        if (scientificName == null || scientificName.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("BAD_REQUEST", "scientificName is required", null));
        }

        PerenualPreviewView view = perenualAjaxService.previewByScientificName(scientificName);
        return ResponseEntity.ok(view);
    }

    @GetMapping(value = "/card", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> card(@RequestParam("query") String query) {
        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("BAD_REQUEST", "Введите название растения", null));
        }

        PerenualSearchCardView view = perenualAjaxService.searchCard(query);
        return ResponseEntity.ok(view);
    }

    @PostMapping(value = "/import", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> importSpecies(@RequestBody ImportRequest req) {
        if (req == null || req.perenualId() <= 0) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ApiErrorResponse("BAD_REQUEST", "perenualId must be positive", null));
        }

        PerenualImportedSpeciesView imported = perenualAjaxService.importByPerenualId(req.perenualId());
        return ResponseEntity.ok(imported);
    }

    public record ImportRequest(long perenualId) {
    }
}