package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.album.AlbumCreateDetails;
import ru.vibeart.api.dtos.album.AlbumResponse;
import ru.vibeart.api.dtos.album.AlbumUpdateDetails;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для работы с альбомами.
 */
public interface AlbumService {
    /**
     * Возвращает постраничный список альбомов автора по его UUID.
     *
     * @param authorUuid UUID автора альбомов (пользователя или сообщества)
     * @param pageable параметры пагинации
     * @return страница с данными альбомов
     */
    Page<AlbumResponse> getAlbumsByUserOrCommunity(UUID authorUuid, Pageable pageable);

    /**
     * Возвращает альбом по его UUID.
     *
     * @param uuid UUID альбома
     * @return объект с данными альбома
     */
    AlbumResponse getAlbumByUuid(UUID uuid);

    /**
     * Создаёт альбом от имени пользователя или сообщества.
     *
     * @param albumCreateDetails объект с данными нового альбома
     * @param file изображение (обложка) альбома
     * @return объект с данными созданного альбома
     */
    AlbumResponse createAlbum(AlbumCreateDetails albumCreateDetails, MultipartFile file);

    /**
     * Изменяет данные альбома.
     *
     * @param uuid UUID альбома
     * @param albumUpdateDetails объект с новыми данными альбома
     * @param file новое изображение альбома, или {@code null}, если не заменяется
     * @return объект с обновлёнными данными альбома
     */
    AlbumResponse updateAlbum(UUID uuid, AlbumUpdateDetails albumUpdateDetails, MultipartFile file);

    /**
     * Добавляет публикации в альбом.
     *
     * @param albumUuid UUID альбома
     * @param postUuids список UUID публикаций для добавления
     */
    void addPostsToAlbum(UUID albumUuid, List<UUID> postUuids);

    /**
     * Удаляет альбом по UUID.
     *
     * @param uuid UUID альбома
     */
    void deleteAlbumByUuid(UUID uuid);
}
