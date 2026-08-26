package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.user.*;

import java.util.Locale;
import java.util.UUID;

/**
 * Сервис для работы с данными пользователя.
 */
public interface UserService {
    /**
     * Возвращает данные текущего аутентифицированного пользователя.
     *
     * @return объект с данными пользователя, необходимыми для отображения в текущей аутентифицированной сессии
     */
    UserDetailResponse getPrincipalUser();

    /**
     * Возвращает данные пользователя по его UUID.
     *
     * @param id UUID пользователя
     * @return объект с данными пользователя
     */
    UserResponse getUserByUUID(UUID id);

    /**
     * Изменяет данные пользователя по его UUID.
     *
     * @param id UUID пользователя
     * @param userUpdateDetails объект с новыми данными пользователя
     * @param file новый аватар пользователя
     * @return объект с данными пользователя
     */
    UserResponse updateUserByUUID(UUID id, UserUpdateDetails userUpdateDetails, MultipartFile file);

    /**
     * Отправляет код подтверждения на почту для изменения адреса электронной почты.
     *
     * @param id UUID пользователя
     * @param changeEmailRequest объект с адресом электронной почты пользователя
     * @param locale объект locale с текущим языком пользователя
     */
    void changeEmail(UUID id, ChangeEmailRequest changeEmailRequest, Locale locale);

    /**
     * Отправляет повторный код подтверждения на почту для изменения адреса электронной почты.
     *
     * @param sendCodeEmailRequest объект с адресом почты для повторной отправки кода
     * @param locale объект locale с текущим языком пользователя
     */
    void sendChangeEmail(SendCodeEmailRequest sendCodeEmailRequest, Locale locale);

    /**
     * Подтверждает изменение адреса электронной почты.
     *
     * @param confirmChangeEmailRequest объект с адресом электронной почты и кодом подтверждения
     */
    void confirmChangeEmail(ConfirmChangeEmailRequest confirmChangeEmailRequest);

    /**
     * Отправляет код подтверждения на почту для изменения пароля.
     *
     * @param id UUID пользователя
     * @param changePasswordRequest объект со старым, новым паролями и подтверждением пароля
     * @param locale объект locale с текущим языком пользователя
     */
    void changePassword(UUID id, ChangePasswordRequest changePasswordRequest, Locale locale);

    /**
     * Отправляет повторный код подтверждения на почту для изменения пароля.
     *
     * @param sendCodePasswordRequest объект с адресом почты для повторной отправки кода
     * @param locale объект locale с текущим языком пользователя
     */
    void sendChangePassword(SendCodePasswordRequest sendCodePasswordRequest, Locale locale);

    /**
     * Подтверждает изменение пароля.
     *
     * @param confirmChangePasswordRequest объект с адресом электронной почты и кодом подтверждения
     */
    void confirmChangePassword(ConfirmChangePasswordRequest confirmChangePasswordRequest);

    /**
     * Удаляет пользователя по UUID
     *
     * @param id UUID пользователя
     */
    void deleteUserByUUID(UUID id);

    /**
     * Переключает подписку текущего пользователя на другого пользователя.
     *
     * @param id UUID пользователя, на которого оформляется или отменяется подписка
     */
    void toggleSubscription(UUID id);

    /**
     * Возвращает постраничный список друзей текущего пользователя — тех, с кем
     * оформлена взаимная подписка.
     *
     * @param pageable параметры пагинации
     * @return страница с данными друзей
     */
    Page<UserResponse> getFriends(Pageable pageable);

    /**
     * Ищет среди друзей текущего пользователя по имени или имени пользователя (username).
     *
     * @param query поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с найденными друзьями
     */
    Page<UserResponse> getFriendsBySearch(String query, Pageable pageable);
}
