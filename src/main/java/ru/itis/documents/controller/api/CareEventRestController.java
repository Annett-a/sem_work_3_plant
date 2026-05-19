package ru.itis.documents.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.itis.documents.domain.entity.CareEvent;
import ru.itis.documents.domain.enums.CareActionType;
import ru.itis.documents.dto.ApiErrorResponse;
import ru.itis.documents.dto.view.CareEventView;
import ru.itis.documents.security.AppUserPrincipal;
import ru.itis.documents.service.CareEventService;

import java.time.OffsetDateTime;
import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/care-events")
@RequiredArgsConstructor
@Tag(name = "Care events", description = "REST CRUD для событий ухода (CareEvent)")
public class CareEventRestController {

    private final CareEventService careEventService;

    @GetMapping
    @Operation(summary = "Список событий ухода", description = "Возвращает события ухода текущего пользователя. Можно отфильтровать по plantId.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CareEventView.class))),
            @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Растение не найдено (если указан plantId)", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public List<CareEventView> list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Parameter(description = "ID растения пользователя (UserPlant). Если передан — возвращаются события только по этому растению.", example = "45")
            @RequestParam(required = false) Long plantId
    ) {
        Long userId = principal.getUser().getId();
        try {
            return careEventService.listMyEvents(userId, plantId).stream()
                    .map(CareEventRestController::toView)
                    .toList();
        } catch (CareEventService.UserPlantNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    @GetMapping("/{id}")
    @Operation(summary = "Детали события", description = "Возвращает одно событие ухода по ID (доступно только владельцу растения).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CareEventView.class))),
            @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Событие не найдено", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CareEventView details(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Parameter(description = "ID события", example = "123")
            @PathVariable Long id
    ) {
        Long userId = principal.getUser().getId();
        try {
            return toView(careEventService.getMyEvent(userId, id));
        } catch (CareEventService.CareEventNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping
    @Operation(summary = "Создать событие", description = "Создаёт событие ухода для растения пользователя и применяет его к плану ухода.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Создано", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CareEventView.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Растение не найдено", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<CareEventView> create(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Valid @RequestBody CareEventCreateRequest body
    ) {
        Long userId = principal.getUser().getId();
        try {
            CareEvent e = careEventService.createEvent(
                    userId,
                    body.userPlantId(),
                    body.type(),
                    body.eventTime(),
                    body.comment()
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(toView(e));
        } catch (CareEventService.UserPlantNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    @PutMapping("/{id}")
    @Operation(summary = "Обновить событие", description = "Обновляет событие ухода (только владелец).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = CareEventView.class))),
            @ApiResponse(responseCode = "400", description = "Некорректный запрос", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Событие не найдено", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CareEventView update(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Parameter(description = "ID события", example = "123")
            @PathVariable Long id,
            @Valid @RequestBody CareEventUpdateRequest body
    ) {
        Long userId = principal.getUser().getId();
        try {
            CareEvent e = careEventService.updateEvent(
                    userId,
                    id,
                    body.type(),
                    body.eventTime(),
                    body.comment()
            );
            return toView(e);
        } catch (CareEventService.CareEventNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить событие", description = "Удаляет событие ухода (только владелец).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Удалено"),
            @ApiResponse(responseCode = "401", description = "Не выполнен вход", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Доступ запрещён", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Событие не найдено", content = @Content(mediaType = "application/json",
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @Parameter(description = "ID события", example = "123")
            @PathVariable Long id
    ) {
        Long userId = principal.getUser().getId();
        try {
            careEventService.deleteEvent(userId, id);
            return ResponseEntity.noContent().build();
        } catch (CareEventService.CareEventNotFoundException ex) {
            throw new ResponseStatusException(NOT_FOUND, ex.getMessage());
        }
    }

    private static CareEventView toView(CareEvent e) {
        return new CareEventView(
                e.getId(),
                e.getUserPlant() == null ? null : e.getUserPlant().getId(),
                e.getType(),
                e.getEventTime(),
                e.getComment()
        );
    }

    @Schema(name = "CareEventCreateRequest", description = "Запрос на создание события ухода")
    public record CareEventCreateRequest(
            @NotNull(message = "userPlantId обязателен")
            @Schema(description = "ID растения пользователя (UserPlant)", example = "45")
            Long userPlantId,

            @NotNull(message = "type обязателен")
            @Schema(description = "Тип действия", example = "WATER")
            CareActionType type,

            @Schema(description = "Дата/время события. Если не задано — будет использовано текущее время.", example = "2026-03-02T12:34:56+01:00")
            OffsetDateTime eventTime,

            @Size(max = 1000, message = "Комментарий слишком длинный (макс 1000)")
            @Schema(description = "Комментарий (опционально)", example = "Полив после пересадки")
            String comment
    ) {
    }

    @Schema(name = "CareEventUpdateRequest", description = "Запрос на обновление события ухода")
    public record CareEventUpdateRequest(
            @Schema(description = "Новый тип действия (опционально)", example = "FERTILIZE")
            CareActionType type,

            @Schema(description = "Новое время события (опционально)", example = "2026-03-02T12:34:56+01:00")
            OffsetDateTime eventTime,

            @Size(max = 1000, message = "Комментарий слишком длинный (макс 1000)")
            @Schema(description = "Комментарий (опционально)", example = "Внесена подкормка")
            String comment
    ) {
    }
}