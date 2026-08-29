package ru.vibeart.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vibeart.api.models.entities.Comment;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link Comment}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет методы для постраничного поиска
 * комментариев публикации и поиска комментария по UUID.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным комментариев публикаций в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findAllByPostUuid(UUID, Pageable)} — постраничный список комментариев публикации;</li>
 *   <li>{@link #findByUuid(UUID)} — поиск комментария по UUID.</li>
 * </ul>
 *
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {
    /**
     * Ищет комментарии публикации.
     *
     * @param postUuid UUID публикации
     * @param pageable параметры пагинации
     * @return страница с найденными комментариями
     */
    Page<Comment> findAllByPostUuid(UUID postUuid, Pageable pageable);

    /**
     * Ищет комментарий по уникальному идентификатору (UUID).
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденный комментарий, если он существует
     */
    Optional<Comment> findByUuid(UUID uuid);
}
