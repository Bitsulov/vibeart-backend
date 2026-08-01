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
import ru.vibeart.api.dtos.post.PostCreateDetails;
import ru.vibeart.api.dtos.post.PostResponse;
import ru.vibeart.api.dtos.post.PostUpdateDetails;
import ru.vibeart.api.services.PostService;

import java.util.UUID;


@Controller
@RequestMapping("/api/post")
@Tag(
        name = "Публикация",
        description = "Получение, создание, изменение, удаление публикаций пользователями или сообществами, а также переключение лайков от пользователей"
)
public class PostController {
    private final PostService postService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param postService сервис данных публикаций
     */
    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(
            summary = "Получение списка постов с пагинацией",
            description = "Возвращает постраничный список публикаций. Если передан albumUuid, список фильтруется по указанному альбому.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список публикаций успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Альбом с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping()
    public ResponseEntity<Page<PostResponse>> getPosts(
            @Parameter(description = "UUID альбома для фильтрации публикаций")
            @RequestParam(required = false) UUID albumUuid,
            Pageable pageable
    ) {
        Page<PostResponse> response = postService.getPosts(albumUuid, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение списка постов автора с пагинацией",
            description = "Возвращает постраничный список публикаций автора. Если передан albumUuid, посты, принадлежащие альбому, исключаются.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список публикаций автора успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Автор публикаций или альбом с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/author")
    public ResponseEntity<Page<PostResponse>> getPostsByAuthor(
            @Parameter(description = "UUID автора публикаций", required = true)
            @RequestParam(required = true) UUID authorUuid,
            @Parameter(description = "UUID альбома, публикации которого нужно исключить из результата")
            @RequestParam(required = false) UUID albumUuid,
            Pageable pageable
    ) {
        Page<PostResponse> response = postService.getPostsByAuthor(authorUuid, albumUuid, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Полнотекстовый поиск публикаций",
            description = "Ищет публикации по заголовку и описанию, результаты отсортированы по релевантности запросу.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список найденных публикаций успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/search")
    public ResponseEntity<Page<PostResponse>> getPostsBySearch(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query,
            Pageable pageable
    ) {
        Page<PostResponse> response = postService.getPostsBySearch(query, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение публикации по UUID",
            description = "Находит публикацию по переданному UUID и возвращает.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные публикации успешно получены"),
                    @ApiResponse(responseCode = "404", description = "Публикация не найдена"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getPostByUuid(
            @Parameter(description = "UUID публикации", required = true)
            @PathVariable UUID id
    ) {
        PostResponse response = postService.getPostByUuid(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Создание публикации",
            description = "Создаёт публикацию от имени пользователя или сообщества.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Публикация успешно создана"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Пользователь не вправе создать пост от имени указанного автора"),
                    @ApiResponse(responseCode = "404", description = "Автор публикации или один из тегов не найден"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> createPost(
            @Parameter(
                    description = "Данные новой публикации",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart("info") @Valid PostCreateDetails postCreateDetails,
            @Parameter(description = "Изображение публикации", required = true)
            @RequestPart("file") MultipartFile file
    ) {
        PostResponse response = postService.createPost(postCreateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Изменение публикации по UUID",
            description = "Находит публикацию по UUID и изменяет её данные.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Публикация успешно изменена"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором публикации"),
                    @ApiResponse(responseCode = "404", description = "Автор, публикация или один из тегов не найден"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PostResponse> editPost(
            @Parameter(description = "UUID публикации", required = true)
            @PathVariable UUID id,
            @Parameter(
                    description = "Новые данные публикации",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart("info") @Valid PostUpdateDetails postUpdateDetails,
            @Parameter(description = "Новое изображение публикации")
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        PostResponse response = postService.updatePost(id, postUpdateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Переключение лайка публикации",
            description = "Ставит или снимает лайк текущего пользователя на публикации.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Лайк успешно переключён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или публикация не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/like")
    public ResponseEntity<String> like(
            @Parameter(description = "UUID публикации", required = true)
            @PathVariable UUID id
    ) {
        postService.toggleLike(id);
        return new ResponseEntity<>("Toggled like successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Жалоба на публикацию",
            description = "Регистрирует жалобу текущего пользователя на публикацию.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Жалоба успешно зарегистрирована"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или публикация не найдены"),
                    @ApiResponse(responseCode = "409", description = "Пользователь уже жаловался на эту публикацию"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/report")
    public ResponseEntity<String> report(
            @Parameter(description = "UUID публикации", required = true)
            @PathVariable UUID id
    ) {
        postService.report(id);
        return new ResponseEntity<>("Post reported successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление публикации по UUID",
            description = "Находит публикацию по UUID и удаляет.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Публикация успешно удалена"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не автором публикации"),
                    @ApiResponse(responseCode = "404", description = "Публикация не найдена"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, удаления изображения или сервера")
            }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deletePost(
            @Parameter(description = "UUID публикации", required = true)
            @PathVariable UUID id
    ) {
        postService.deletePostByUuid(id);
        return new ResponseEntity<>("Post deleted successfully", HttpStatus.OK);
    }
}
