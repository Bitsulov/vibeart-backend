package ru.vibeart.api.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vibeart.api.models.entities.Post;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link Post}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет методы для постраничного поиска
 * по связанным сущностям, поиска по UUID и обновления счётчика лайков.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным публикаций в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findAll(Pageable)} — постраничный вывод всех публикаций;</li>
 *   <li>{@link #findAllByAlbumsUuid(UUID, Pageable)} — поиск публикаций по альбому;</li>
 *   <li>{@link #findAllByAuthorUserUuid(UUID, Pageable)} — поиск публикаций по автору-пользователю;</li>
 *   <li>{@link #findAllByAuthorCommunityUuid(UUID, Pageable)} — поиск публикаций по автору-сообществу;</li>
 *   <li>{@link #findAllByAuthorUserUuidExcludingAlbum(UUID, UUID, Pageable)} — поиск публикаций по автору-пользователю
 *   с исключением публикаций из указанного альбома;</li>
 *   <li>{@link #findAllByAuthorCommunityUuidExcludingAlbum(UUID, UUID, Pageable)} — поиск публикаций по автору-сообществу
 *   с исключением публикаций из указанного альбома;</li>
 *   <li>{@link #findByUuid(UUID)} — поиск публикации по UUID;</li>
 *   <li>{@link #incrementLikesCount(Long)} — увеличение счётчика лайков публикации;</li>
 *   <li>{@link #decrementLikesCount(Long)} — уменьшение счётчика лайков публикации.</li>
 * </ul>
 *
 */
public interface PostRepository extends JpaRepository<Post, Long> {
    /**
     * Возвращает страницу всех публикаций.
     *
     * @param pageable параметры пагинации
     * @return страница со всеми публикациями
     */
    Page<Post> findAll(Pageable pageable);

    /**
     * Ищет публикации, входящие в альбом.
     *
     * @param uuid UUID альбома
     * @param pageable параметры пагинации
     * @return страница с найденными публикациями
     */
    Page<Post> findAllByAlbumsUuid(UUID uuid, Pageable pageable);

    /**
     * Ищет публикации по автору-пользователю.
     *
     * @param uuid UUID пользователя-автора
     * @param pageable параметры пагинации
     * @return страница с найденными публикациями
     */
    Page<Post> findAllByAuthorUserUuid(UUID uuid, Pageable pageable);

    /**
     * Ищет публикации по автору-сообществу.
     *
     * @param uuid UUID сообщества-автора
     * @param pageable параметры пагинации
     * @return страница с найденными публикациями
     */
    Page<Post> findAllByAuthorCommunityUuid(UUID uuid, Pageable pageable);

    /**
     * Ищет публикации по автору-пользователю, исключая публикации, входящие в указанный альбом.
     * <p>
     * Используется отдельный запрос (а не производный метод с {@code AlbumsUuidNot}), поскольку
     * для ManyToMany-связи производное имя транслируется в join-условие и даёт неверный результат:
     * пост с несколькими альбомами не будет исключён, если хотя бы один из его альбомов
     * отличается от переданного UUID.
     * </p>
     *
     * @param authorUuid UUID пользователя-автора
     * @param albumUuid UUID альбома, публикации которого нужно исключить
     * @param pageable параметры пагинации
     * @return страница с найденными публикациями
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorUser.uuid = :authorUuid
              AND NOT EXISTS (SELECT 1 FROM p.albums a WHERE a.uuid = :albumUuid)
            """)
    Page<Post> findAllByAuthorUserUuidExcludingAlbum(
            @Param("authorUuid") UUID authorUuid,
            @Param("albumUuid") UUID albumUuid,
            Pageable pageable
    );

    /**
     * Ищет публикации по автору-сообществу, исключая публикации, входящие в указанный альбом.
     *
     * @param authorUuid UUID сообщества-автора
     * @param albumUuid UUID альбома, публикации которого нужно исключить
     * @param pageable параметры пагинации
     * @return страница с найденными публикациями
     */
    @Query("""
            SELECT p FROM Post p
            WHERE p.authorCommunity.uuid = :authorUuid
              AND NOT EXISTS (SELECT 1 FROM p.albums a WHERE a.uuid = :albumUuid)
            """)
    Page<Post> findAllByAuthorCommunityUuidExcludingAlbum(
            @Param("authorUuid") UUID authorUuid,
            @Param("albumUuid") UUID albumUuid,
            Pageable pageable
    );

    /**
     * Ищет публикацию по уникальному идентификатору (UUID).
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденную публикацию, если она существует
     */
    Optional<Post> findByUuid(UUID uuid);

    /**
     * Ищет публикации по списку UUID.
     *
     * @param uuids список UUID публикаций
     * @return список найденных публикаций
     */
    List<Post> findAllByUuidIn(List<UUID> uuids);

    /**
     * Ищет публикацию по UUID и блокирует найденную строку до конца транзакции,
     * чтобы конкурентные запросы к одному и тому же посту выполнялись по очереди.
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденную публикацию, если она существует
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Post> findWithLockByUuid(UUID uuid);

    /**
     * Увеличивает счётчик лайков публикации на единицу.
     *
     * @param id внутренний идентификатор публикации
     */
    @Modifying
    // UPDATE posts SET likes_count = likes_count + 1 WHERE id =
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikesCount(Long id);

    /**
     * Уменьшает счётчик лайков публикации на единицу.
     *
     * @param id внутренний идентификатор публикации
     */
    @Modifying
    // UPDATE posts SET likes_count = likes_count - 1 WHERE id =
    @Query("UPDATE Post p SET p.likesCount = p.likesCount - 1 WHERE p.id = :id")
    void decrementLikesCount(Long id);

    /**
     * Увеличивает счётчик жалоб публикации на единицу.
     *
     * @param id внутренний идентификатор публикации
     */
    @Modifying
    // UPDATE posts SET reports_count = reports_count + 1 WHERE id =
    @Query("UPDATE Post p SET p.reportsCount = p.reportsCount + 1 WHERE p.id = :id")
    void incrementReportsCount(Long id);

    /**
     * Ищет публикации полнотекстовым поиском PostgreSQL по заголовку и описанию,
     * результаты сортируются по релевантности ({@code ts_rank}).
     * <p>
     * Использует функциональный GIN-индекс {@code posts_search_idx} (см. {@code schema.sql}).
     * Сортировка, переданная в {@code pageable}, игнорируется — порядок всегда определяется
     * релевантностью запросу.
     * </p>
     *
     * @param query поисковый запрос пользователя
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными публикациями, отсортированными по релевантности
     */
    @Query(
            value = """
                SELECT * FROM posts p
                WHERE to_tsvector('russian', p.title || ' ' || coalesce(p.description, ''))
                      @@ plainto_tsquery('russian', :query)
                ORDER BY ts_rank(
                    to_tsvector('russian', p.title || ' ' || coalesce(p.description, '')),
                    plainto_tsquery('russian', :query)
                ) DESC
                """,
            countQuery = """
                SELECT count(*) FROM posts p
                WHERE to_tsvector('russian', p.title || ' ' || coalesce(p.description, ''))
                      @@ plainto_tsquery('russian', :query)
                """,
            nativeQuery = true
    )
    Page<Post> searchFullText(@Param("query") String query, Pageable pageable);
}
