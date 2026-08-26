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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.user.*;
import ru.vibeart.api.services.UserService;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("api/user")
@Tag(
        name = "Пользователь",
        description = "Получение, изменение, удаление, изменения адреса электронной почты, изменение пароля пользователя"
)
public class UserController {
    private final UserService userService;

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param userService сервис данных пользователя
     */
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(
            summary = "Получение пользователя по UUID",
            description = "Находит пользователя по переданному UUID и возвращает.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные пользователя успешно получены"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserByUUID(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id
    ) {
        UserResponse response = userService.getUserByUUID(id);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Изменение данных пользователя по UUID",
            description = "Находит пользователя по переданному UUID, изменяет данные и возвращает измененного пользователя.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Данные пользователя успешно изменены"),
                    @ApiResponse(responseCode = "400", description = "Пользователь указывает удаление аватара, но присылает файл"),
                    @ApiResponse(responseCode = "403", description = "Пользователь пытается изменить чужие данные"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "409", description = "Переданное имя пользователя уже занято"),
                    @ApiResponse(responseCode = "413", description = "Файл слишком большого размера"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных, загрузки файла или сервера")
            }
    )
    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("#id == authentication.principal.uuid")
    public ResponseEntity<UserResponse> updateUserByUUID(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id,
            @Parameter(
                    description = "Изменяемые данные пользователя",
                    required = true,
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)
            )
            @RequestPart("info") @Valid UserUpdateDetails userUpdateDetails,
            @Parameter(description = "Новый аватар пользователя")
            @RequestPart(value = "file", required = false) MultipartFile file
    ) {
        UserResponse response = userService.updateUserByUUID(id, userUpdateDetails, file);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Изменение адреса электронной почты пользователя",
            description = "Находит пользователю по переданному UUID и отправляет код подтверждения на адрес электронной почты.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Смена адреса электронной почты успешно инициирована"),
                    @ApiResponse(responseCode = "400", description = "Запрос отправлен слишком рано после предыдущего"),
                    @ApiResponse(responseCode = "403", description = "Пользователь пытается изменить чужой адрес электронной почты"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "409", description = "Переданный адрес электронной почты уже занят"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/email")
    @PreAuthorize("#id == authentication.principal.uuid")
    public ResponseEntity<String> changeEmail(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Новый адрес электронной почты", required = true)
            @Valid @RequestBody ChangeEmailRequest request,
            @Parameter(description = "Язык клиента из заголовка Access-language")
            Locale locale
    ) {
        userService.changeEmail(id, request, locale);
        return new ResponseEntity<>("Changing email initiated. Check your email for code", HttpStatus.OK);
    }

    @Operation(
            summary = "Повторная отправка кода подтверждения смены адреса электронной почты",
            description = "Удаляет старый код подтверждения, создаёт новый и отправляет письмо на новый адрес электронной почты.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код подтверждения отправлен повторно"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "400", description = "Повторный код запрошен слишком рано"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или старый код подтверждения не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/email/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> sendChangeEmail(
            @Parameter(description = "Новый адрес электронной почты", required = true)
            @Valid @RequestBody SendCodeEmailRequest request,
            @Parameter(description = "Язык клиента из заголовка Access-language")
            Locale locale
    ) {
        userService.sendChangeEmail(request, locale);
        return new ResponseEntity<>("Code sent. Check your email for verification code.", HttpStatus.OK);
    }

    @Operation(
            summary = "Подтверждение изменение адреса электронной почты пользователя",
            description = "Проверяет введённый код и меняет email.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Адрес электронной почты успешно изменён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "400", description = "Неверный код подтверждения"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "410", description = "Код подтверждения истек"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/email/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> confirmChangeEmail(
            @Parameter(description = "Новый адрес электронной почты c кодом подтверждения", required = true)
            @Valid @RequestBody ConfirmChangeEmailRequest request
    ) {
        userService.confirmChangeEmail(request);
        return new ResponseEntity<>("Email was changed successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Изменение пароля пользователя",
            description = "Находит пользователю по переданному UUID и отправляет код подтверждения на адрес электронной почты.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Смена пароля успешно инициирована"),
                    @ApiResponse(responseCode = "400", description = "Неверный старый пароль, пароли не совпадают, старый и новый пароли одинаковые или запрос отправлен слишком рано"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/password")
    @PreAuthorize("#id == authentication.principal.uuid")
    public ResponseEntity<String> changePassword(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id,
            @Parameter(description = "Старый пароль, новый пароль и подтверждение пароля", required = true)
            @Valid @RequestBody ChangePasswordRequest request,
            @Parameter(description = "Язык клиента из заголовка Access-language")
            Locale locale
    ) {
        userService.changePassword(id, request, locale);
        return new ResponseEntity<>("Changing password initiated. Check your email for code", HttpStatus.OK);
    }

    @Operation(
            summary = "Повторная отправка кода подтверждения смены пароля",
            description = "Удаляет старый код подтверждения, создаёт новый и отправляет письмо на адрес электронной почты.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Код подтверждения отправлен повторно"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "400", description = "Повторный код запрошен слишком рано"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или старый код подтверждения не найдены"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/password/send")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> sendChangePassword(
            @Parameter(description = "Адрес электронной почты", required = true)
            @Valid @RequestBody SendCodePasswordRequest request,
            @Parameter(description = "Язык клиента из заголовка Access-language")
            Locale locale
    ) {
        userService.sendChangePassword(request, locale);
        return new ResponseEntity<>("Code sent. Check your email for verification code.", HttpStatus.OK);
    }

    @Operation(
            summary = "Подтверждение изменение пароля пользователя",
            description = "Проверяет введённый код и меняет пароль.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Адрес электронной почты успешно изменён"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "400", description = "Неверный код подтверждения или адрес электронной почты"),
                    @ApiResponse(responseCode = "404", description = "Пользователь или код подтверждения не найдены"),
                    @ApiResponse(responseCode = "410", description = "Код подтверждения истек"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/password/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> confirmChangePassword(
            @Parameter(description = "Адрес электронной почты пользователя c кодом подтверждения", required = true)
            @Valid @RequestBody ConfirmChangePasswordRequest request
    ) {
        userService.confirmChangePassword(request);
        return new ResponseEntity<>("Password was changed successfully", HttpStatus.OK);
    }

    @Operation(
            summary = "Удаление пользователя по UUID",
            description = "Находит пользователя по переданному UUID и удаляет.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Пользователь успешно удалён"),
                    @ApiResponse(responseCode = "403", description = "Пользователь пытается удалить другого пользователя"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @DeleteMapping("/{id}")
    @PreAuthorize("#id == authentication.principal.uuid")
    public ResponseEntity<String> deletePrincipalUser(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id
    ) {
        userService.deleteUserByUUID(id);
        return new ResponseEntity<>("User was deleted correctly", HttpStatus.OK);
    }

    @Operation(
            summary = "Получение списка друзей текущего пользователя с пагинацией",
            description = "Возвращает постраничный список друзей текущего пользователя — тех, с кем оформлена взаимная подписка.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список друзей успешно получен"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/friends")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserResponse>> getFriends(Pageable pageable) {
        Page<UserResponse> response = userService.getFriends(pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Поиск среди друзей текущего пользователя",
            description = "Ищет среди друзей текущего пользователя по имени или имени пользователя (username). " +
                    "Если запрос начинается с @, поиск идёт по имени пользователя.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Список найденных друзей успешно получен"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @GetMapping("/friends/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<UserResponse>> getFriendsBySearch(
            @Parameter(description = "Поисковый запрос", required = true)
            @RequestParam String query,
            Pageable pageable
    ) {
        Page<UserResponse> response = userService.getFriendsBySearch(query, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Operation(
            summary = "Переключение подписки на пользователя",
            description = "Оформляет или отменяет подписку текущего пользователя на другого пользователя.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Подписка успешно переключена"),
                    @ApiResponse(responseCode = "400", description = "Пользователь пытается подписаться на самого себя"),
                    @ApiResponse(responseCode = "401", description = "Пользователь не авторизован"),
                    @ApiResponse(responseCode = "404", description = "Пользователь не найден"),
                    @ApiResponse(responseCode = "500", description = "Ошибка базы данных или сервера")
            }
    )
    @PostMapping("/{id}/subscribe")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<String> subscribe(
            @Parameter(description = "UUID пользователя", required = true)
            @PathVariable UUID id
    ) {
        userService.toggleSubscription(id);
        return new ResponseEntity<>("Subscription toggled successfully", HttpStatus.OK);
    }
}
