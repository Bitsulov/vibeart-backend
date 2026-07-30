package ru.vibeart.api.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.album.AlbumAddPostsRequest;
import ru.vibeart.api.dtos.album.AlbumCreateDetails;
import ru.vibeart.api.dtos.album.AlbumResponse;
import ru.vibeart.api.dtos.album.AlbumUpdateDetails;
import ru.vibeart.api.services.AlbumService;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("api/album")
@Tag(
        name = "Альбом",
        description = "Получение, создание, изменение, удаление альбомов пользователями или сообществами"
)
public class AlbumController {
    private final AlbumService albumService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param albumService сервис данных альбомов
     */
    public AlbumController(AlbumService albumService) {
        this.albumService = albumService;
    }

    @Operation(
            summary = "Получение списка альбомов автора с пагинацией",
            description = "Возвращает постраничный список альбомов пользователя или сообщества по UUID автора.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список альбомов успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Автор альбомов не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping
    public ResponseEntity<Page<AlbumResponse>> getAlbumsByUserOrCommunity(
            @Parameter(description = "UUID автора альбомов", required = true)
            @RequestParam UUID authorUuid,
            Pageable pageable
    ) {
        Page<AlbumResponse> response = albumService.getAlbumsByUserOrCommunity(authorUuid, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение альбома по UUID",
            description = "Находит альбом по переданному UUID и возвращает.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные альбома успешно получены"),
                    @ApiResponse(responseCode = "404", description = "Альбом не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponse> getAlbumByUuid(
            @Parameter(description = "UUID альбома", required = true)
            @PathVariable UUID id
    ) {
        AlbumResponse response = albumService.getAlbumByUuid(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Создание альбома",
            description = "Создаёт альбом от имени пользователя или сообщества.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Альбом успешно создан"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Пользователь не вправе создать альбом от имени указанного автора"),
                    @ApiResponse(responseCode = "404", description = "Текущий пользователь или автор альбома не найден"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumResponse> createAlbum(
            @Parameter(
                    description = "Данные нового альбома",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart(name = "info") @Valid AlbumCreateDetails albumCreateDetails,
            @Parameter(description = "Изображение альбома", required = true)
            @RequestPart(name = "file") MultipartFile file
    ) {
        AlbumResponse response = albumService.createAlbum(albumCreateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Изменение альбома по UUID",
            description = "Находит альбом по UUID и изменяет его данные.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Альбом успешно изменён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором альбома"),
                    @ApiResponse(responseCode = "404", description = "Текущий пользователь или альбом не найден"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AlbumResponse> updateAlbum(
            @Parameter(description = "UUID альбома", required = true)
            @PathVariable UUID id,
            @Parameter(
                    description = "Новые данные альбома",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart(name = "info") @Valid AlbumUpdateDetails albumUpdateDetails,
            @Parameter(description = "Новое изображение альбома")
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        AlbumResponse response = albumService.updateAlbum(id, albumUpdateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Добавление публикаций в альбом",
            description = "Добавляет публикации по списку UUID в альбом.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Публикации успешно добавлены в альбом"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором альбома, либо один из постов принадлежит другому автору"),
                    @ApiResponse(responseCode = "404", description = "Текущий пользователь, альбом или одна из публикаций не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/posts")
    public ResponseEntity<String> addPostsToAlbum(
            @Parameter(description = "UUID альбома", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Список UUID публикаций для добавления", required = true)
            @RequestBody AlbumAddPostsRequest albumAddPostsRequest
    ) {
        albumService.addPostsToAlbum(id, albumAddPostsRequest);
        return new ResponseEntity<>("Album was updated successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление альбома по UUID",
            description = "Находит альбом по UUID и удаляет.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Альбом успешно удалён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором альбома"),
                    @ApiResponse(responseCode = "404", description = "Текущий пользователь или альбом не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, удаления изображения или сервера")
            }
    )
    @DeleteMapping(value = "/{id}")
    public ResponseEntity<String> deleteAlbum(
            @Parameter(description = "UUID альбома", required = true)
            @PathVariable UUID id
    ) {
        albumService.deleteAlbumByUuid(id);
        return new ResponseEntity<>("Album was deleted successfuly", HttpStatus.OK);
    }
}
