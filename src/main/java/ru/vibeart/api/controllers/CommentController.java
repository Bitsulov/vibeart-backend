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
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import ru.vibeart.api.dtos.comment.CommentCreateRequest;
import ru.vibeart.api.dtos.comment.CommentResponse;
import ru.vibeart.api.dtos.comment.CommentUpdateRequest;
import ru.vibeart.api.services.CommentService;

import java.util.UUID;

@Controller
@RequestMapping("/api/comment")
@Tag(
        name = "Комментарии",
        description = "Получение, создание, изменение и удаление комментариев к публикациям."
)
public class CommentController {
    private final CommentService commentService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param commentService сервис данных комментариев
     */
    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @Operation(
            summary = "Получение списка комментариев публикации с пагинацией",
            description = "Возвращает постраничный список комментариев публикации.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список комментариев успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Публикация с указанным UUID не найдена"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping
    public ResponseEntity<Page<CommentResponse>> getCommentsByPost(
            @Parameter(description = "UUID публикации", required = true)
            @RequestParam UUID postUuid,
            Pageable pageable
    ) {
        Page<CommentResponse> response = commentService.getCommentsByPost(postUuid, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Создание комментария",
            description = "Создаёт комментарий к публикации от имени текущего пользователя.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Комментарий успешно создан"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или публикация не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping
    public ResponseEntity<CommentResponse> createComment(
            @Parameter(description = "Данные нового комментария", required = true)
            @Valid @RequestBody CommentCreateRequest commentCreateRequest
    ) {
        CommentResponse response = commentService.createComment(commentCreateRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Изменение комментария по UUID",
            description = "Находит комментарий по UUID, изменяет его текст и возвращает.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Комментарий успешно изменён"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором комментария"),
                    @ApiResponse(responseCode = "404", description = "Комментарий не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> updateComment(
            @Parameter(description = "UUID комментария", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Новые данные комментария", required = true)
            @Valid @RequestBody CommentUpdateRequest commentUpdateRequest
    ) {
        CommentResponse response = commentService.updateComment(id, commentUpdateRequest);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление комментария по UUID",
            description = "Находит комментарий по UUID и удаляет его.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Комментарий успешно удалён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором комментария"),
                    @ApiResponse(responseCode = "404", description = "Комментарий не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteComment(
            @Parameter(description = "UUID комментария", required = true)
            @PathVariable UUID id
    ) {
        commentService.deleteComment(id);
        return new ResponseEntity<>("Comment deleted successfully", HttpStatus.OK);
    }
}
