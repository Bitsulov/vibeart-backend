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
import ru.vibeart.api.dtos.community.CommunityCreateDetails;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.community.CommunityUpdateDetails;
import ru.vibeart.api.services.CommunityService;

import java.util.UUID;

@Controller
@RequestMapping("/api/community")
@Tag(
        name = "Сообщество",
        description = "Получение, создание, изменение и удаление сообществ."
)
public class CommunityController {
    private final CommunityService communityService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param communityService сервис данных сообществ
     */
    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @Operation(
            summary = "Получение списка сообществ с пагинацией.",
            description = "Возвращает постраничный список сообществ за исключением сообществ, на которые подписан " +
                    "пользователь по переданному UUID, и сообществ, которыми он владеет",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список сообществ успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Пользователь с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping
    public ResponseEntity<Page<CommunityResponse>> getCommunities(
            @Parameter(description = "UUID пользователя")
            @RequestParam(required = false) UUID userId,
            Pageable pageable
    ) {
        Page<CommunityResponse> response = communityService.getCommunities(userId, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение списка сообществ, на которые подписан пользователь с пагинацией",
            description = "Возвращает постраничный список сообществ, на которые подписан пользователь.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список сообществ успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Пользователь с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/user")
    public ResponseEntity<Page<CommunityResponse>> getCommunitiesByUser(
            @Parameter(description = "UUID пользователя", required = true)
            @RequestParam UUID userId,
            Pageable pageable
    ) {
        Page<CommunityResponse> response = communityService.getCommunitiesByUser(userId, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение списка сообществ текущего пользователя с пагинацией",
            description = "Возвращает постраничный список сообществ, владельцем которых является текущий пользователь.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список сообществ успешно получен"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/owned")
    public ResponseEntity<Page<CommunityResponse>> getOwnedCommunities(Pageable pageable) {
        Page<CommunityResponse> response = communityService.getOwnedCommunities(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Полнотекстовый поиск сообществ",
            description = "Ищет сообщества по названию и описанию, результаты отсортированы по релевантности " +
                    "запросу. Если запрос начинается с @, поиск идёт по имени пользователя сообщества. " +
                    "Если передан userId, из результата исключаются сообщества, на которые пользователь " +
                    "уже подписан, и сообщества, которыми он владеет.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список найденных сообществ успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Пользователь с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/search")
    public ResponseEntity<Page<CommunityResponse>> getCommunitiesBySearch(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query,
            @Parameter(description = "UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата")
            @RequestParam(required = false) UUID userId,
            Pageable pageable
    ) {
        Page<CommunityResponse> response = communityService.getCommunitiesBySearch(query, userId, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Полнотекстовый поиск по подпискам пользователя",
            description = "Ищет по названию и описанию среди сообществ, на которые подписан пользователь, " +
                    "результаты отсортированы по релевантности запросу. Если запрос начинается с @, " +
                    "поиск идёт по имени пользователя сообщества.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список найденных сообществ успешно получен"),
                    @ApiResponse(responseCode = "404", description = "Пользователь с указанным UUID не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/user/search")
    public ResponseEntity<Page<CommunityResponse>> getCommunitiesByUserAndSearch(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query,
            @Parameter(description = "UUID пользователя", required = true)
            @RequestParam UUID userId,
            Pageable pageable
    ) {
        Page<CommunityResponse> response = communityService.getCommunitiesByUserAndSearch(query, userId, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Получение сообщества",
            description = "Возвращает сообщество по его UUID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные сообщества успешно получены"),
                    @ApiResponse(responseCode = "404", description = "Сообщество не найдено"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<CommunityResponse> getCommunityByUser(
            @Parameter(description = "UUID сообщества", required = true)
            @PathVariable UUID id
    ) {
        CommunityResponse response = communityService.getCommunityByUuid(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Создание сообщества",
            description = "Создает сообщество и возвращает его.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Сообщество успешно создано"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные или владелец указан в списке администраторов"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Пользователь не вправе создать сообщество от имени указанного пользователя"),
                    @ApiResponse(responseCode = "404", description = "Один из тегов или администраторов не найден"),
                    @ApiResponse(responseCode = "409", description = "Переданное имя пользователя сообщества уже занято"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommunityResponse> createCommunity(
            @Parameter(
                    description = "Данные нового сообщества",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart(name = "info") @Valid CommunityCreateDetails communityCreateDetails,
            @Parameter(description = "Изображение сообщества")
            @RequestPart(name = "file", required = false) MultipartFile file
    ) {
        CommunityResponse response = communityService.createCommunity(communityCreateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @Operation(
            summary = "Изменение сообщества",
            description = "Изменяет сообщество и возвращает его.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Сообщество успешно изменено"),
                    @ApiResponse(responseCode = "400", description = "Неверные данные или владелец указан в списке администраторов"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не владельцем сообщества"),
                    @ApiResponse(responseCode = "404", description = "Сообщество, один из тегов или администраторов не найден"),
                    @ApiResponse(responseCode = "409", description = "Переданное имя пользователя сообщества уже занято"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки изображения или сервера")
            }
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CommunityResponse> updateCommunity(
            @Parameter(description = "UUID сообщества", required = true)
            @PathVariable UUID id,
            @Parameter(
                    description = "Новые данные сообщества",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart(name = "info") @Valid CommunityUpdateDetails communityUpdateDetails,
            @Parameter(description = "Изображение сообщества")
            @RequestPart(name = "file", required = false) MultipartFile file
    ) {
        CommunityResponse response = communityService.updateCommunity(id, communityUpdateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Переключение подписки на сообщество",
            description = "Оформляет или отменяет подписку текущего пользователя на сообщество.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Подписка успешно переключена"),
                    @ApiResponse(responseCode = "400", description = "Пользователь пытается подписаться на собственное сообщество"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или сообщество не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/subscribe")
    public ResponseEntity<String> subscribe(
            @Parameter(description = "UUID сообщества", required = true)
            @PathVariable UUID id
    ) {
        communityService.toggleSubscription(id);
        return new ResponseEntity<>("Subscription toggled successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление сообщества",
            description = "Находит сообщество по UUID и удаляет его",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Сообщество успешно удалено"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "403", description = "Запрос отправлен не владельцем сообщества"),
                    @ApiResponse(responseCode = "404", description = "Сообщество не найдено"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, удаления изображения или сервера")
            }
    )
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteCommunity(
            @Parameter(description = "UUID сообщества", required = true)
            @PathVariable UUID id
    ) {
        communityService.deleteCommunity(id);
        return new ResponseEntity<>("Community was deleted successfully", HttpStatus.OK);
    }
}
