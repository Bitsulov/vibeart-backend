package ru.vibeart.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vibeart.api.models.entities.Album;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link Album}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет методы для поиска по посту и UUID.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным альбомов в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findAllByPostsUuid(UUID)} — поиск альбомов, содержащих пост;</li>
 *   <li>{@link #findByUuid(UUID)} — поиск альбома по UUID.</li>
 * </ul>
 *
 */
public interface AlbumRepository extends JpaRepository<Album, Long> {
    /**
     * Ищет альбомы, в которые входит пост.
     *
     * @param postUuid UUID поста
     * @return список альбомов, содержащих указанный пост
     */
    List<Album> findAllByPostsUuid(UUID postUuid);

    /**
     * Ищет альбом по уникальному идентификатору (UUID).
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденный альбом, если он существует
     */
    Optional<Album> findByUuid(UUID uuid);

    /**
     * Ищет альбомы пользователя по его UUID
     *
     * @param userUuid UUID пользователя
     * @param pageable параметры пагинации
     * @return список альбомов, имеющихся у пользователя
     */
    Page<Album> findAllByAuthorUserUuid(UUID userUuid, Pageable pageable);

    /**
     * Ищет альбомы сообщества по его UUID
     *
     * @param communityUuid UUID сообщества
     * @param pageable параметры пагинации
     * @return список альбомов, имеющихся у сообщества
     */
    Page<Album> findAllByAuthorCommunityUuid(UUID communityUuid, Pageable pageable);
}
