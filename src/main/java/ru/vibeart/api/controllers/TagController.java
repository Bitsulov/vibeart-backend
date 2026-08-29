package ru.vibeart.api.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.vibeart.api.dtos.tag.TagCreateRequest;
import ru.vibeart.api.dtos.tag.TagResponse;
import ru.vibeart.api.dtos.tag.TagUpdateRequest;
import ru.vibeart.api.services.TagService;

@Controller
@RequestMapping("/api/tag")
@Tag(
        name = "Теги",
        description = "Получение, поиск, создание, изменение и удаление тегов."
)
public class TagController {
    private final TagService tagService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param tagService сервис данных тегов
     */
    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @Operation(
            summary = "Получение списка тегов с пагинацией",
            description = "Возвращает постраничный список тегов.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список тегов успешно получен"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping
    public ResponseEntity<Page<TagResponse>> getTags(Pageable pageable) {
        Page<TagResponse> response = tagService.getTags(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Поиск тегов",
            description = "Ищет теги, название которых содержит переданную подстроку, без учёта регистра.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список найденных тегов успешно получен"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/search")
    public ResponseEntity<Page<TagResponse>> searchTags(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query,
            Pageable pageable
    ) {
        Page<TagResponse> response = tagService.searchTags(query, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Создание тега",
            description = "Создаёт тег и возвращает его.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Тег успешно создан"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не администратором"),
                    @ApiResponse(responseCode = "409", description = "Переданное название тега уже занято"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<TagResponse> createTag(
            @Parameter(description = "Данные нового тега", required = true)
            @Valid @RequestBody TagCreateRequest tagCreateDetails
    ) {
        TagResponse response = tagService.createTag(tagCreateDetails);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Изменение тега",
            description = "Находит тег по названию, изменяет его и возвращает.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Тег успешно изменён"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не администратором"),
                    @ApiResponse(responseCode = "404", description = "Тег не найден"),
                    @ApiResponse(responseCode = "409", description = "Переданное название тега уже занято"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PutMapping("/{title}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<TagResponse> updateTag(
            @Parameter(description = "Текущее название тега", required = true)
            @PathVariable String title,
            @Parameter(description = "Новые данные тега", required = true)
            @Valid @RequestBody TagUpdateRequest tagUpdateDetails
    ) {
        TagResponse response = tagService.updateTag(title, tagUpdateDetails);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление тега",
            description = "Находит тег по названию и удаляет его.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Тег успешно удалён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не администратором"),
                    @ApiResponse(responseCode = "404", description = "Тег не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @DeleteMapping("/{title}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> deleteTag(
            @Parameter(description = "Название тега", required = true)
            @PathVariable String title
    ) {
        tagService.deleteTag(title);
        return new ResponseEntity<>("Tag was deleted successfully", HttpStatus.OK);
    }
}
