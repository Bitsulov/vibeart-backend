package ru.vibeart.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vibeart.api.models.entities.Community;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link Community}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет метод для поиска по UUID.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным сообществ в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findAllExcludingUserSubscribesAndOwned(UUID, Pageable)} — поиск сообществ за исключением
 *       подписок и собственных сообществ пользователя;</li>
 *   <li>{@link #findAllUserSubscribed(UUID, Pageable)} — поиск сообществ, на которые подписан пользователь;</li>
 *   <li>{@link #searchFullText(String, UUID, Pageable)} — полнотекстовый поиск сообществ по названию и описанию;</li>
 *   <li>{@link #searchByUsername(String, UUID, Pageable)} — поиск сообществ по имени пользователя;</li>
 *   <li>{@link #searchSubscribedFullText(String, UUID, Pageable)} — полнотекстовый поиск среди подписок пользователя;</li>
 *   <li>{@link #searchSubscribedByUsername(String, UUID, Pageable)} — поиск по имени пользователя среди подписок;</li>
 *   <li>{@link #findByUuid(UUID)} — поиск сообщества по UUID.</li>
 *   <li>{@link #findAllByOwner_Uuid(UUID, Pageable)} — поиск сообществ по владельцу.</li>
 *   <li>{@link #findWithLockByUuid(UUID)} — поиск сообщества по UUID с пессимистической блокировкой строки;</li>
 *   <li>{@link #existsByUsername(String)} — проверка занятости имени пользователя сообщества.</li>
 *   <li>{@link #incrementSubscribersCount(Long)} — увеличение счётчика подписчиков сообщества;</li>
 *   <li>{@link #decrementSubscribersCount(Long)} — уменьшение счётчика подписчиков сообщества.</li>
 * </ul>
 *
 */
public interface CommunityRepository extends JpaRepository<Community, Long> {
    /**
     * Ищет сообщества за исключением сообществ, на которые подписан пользователь, и
     * сообществ, которыми он владеет.
     * <p>
     * Если {@code userId} не передан (равен {@code null}), возвращаются все сообщества
     * без исключений.
     * </p>
     *
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации
     * @return страница сообществ, доступных пользователю для подписки
     */
    @Query(value = """
            SELECT c
            FROM Community c
            WHERE (:userId IS NULL OR c.owner.uuid <> :userId)
              AND (:userId IS NULL OR NOT EXISTS (
                SELECT cs
                FROM CommunitySubscription cs
                WHERE cs.following = c
                    AND cs.follower.uuid = :userId
                    AND cs.active = true
                ))
            """,
        countQuery = """
            SELECT COUNT(c)
            FROM Community c
            WHERE (:userId IS NULL OR c.owner.uuid <> :userId)
              AND (:userId IS NULL OR NOT EXISTS (
                SELECT cs
                FROM CommunitySubscription cs
                WHERE cs.following = c
                  AND cs.follower.uuid = :userId
                  AND cs.active = true
            ))
            """)
    Page<Community> findAllExcludingUserSubscribesAndOwned(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет сообщества, на которые подписан пользователь с указанным UUID.
     *
     * @param userId UUID пользователя, чьи подписки ищутся
     * @param pageable параметры пагинации
     * @return страница сообществ, на которые подписан пользователь
     */
    @Query(value = """
        SELECT c
        FROM Community c
        WHERE EXISTS (
            SELECT cs
            FROM CommunitySubscription cs
            WHERE cs.following = c
              AND cs.follower.uuid = :userId
              AND cs.active = true
            )
        """,
    countQuery = """
        SELECT COUNT(c)
        FROM Community c
        WHERE EXISTS (
            SELECT cs
            FROM CommunitySubscription cs
            WHERE cs.following = c
              AND cs.follower.uuid = :userId
              AND cs.active = true
            )
        """)
    Page<Community> findAllUserSubscribed(
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет сообщества полнотекстовым поиском по названию и описанию,
     * результаты сортируются по релевантности ({@code ts_rank}).
     * <p>
     * Использует функциональный GIN-индекс {@code communities_search_idx} (см. {@code schema.sql}).
     * Если передан {@code userId}, из результата исключаются сообщества, на которые пользователь
     * уже подписан, и сообщества, которыми он владеет. Сортировка, переданная в {@code pageable},
     * игнорируется — порядок всегда определяется релевантностью запросу.
     * </p>
     *
     * @param query поисковый запрос пользователя
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по релевантности
     */
    @Query(
            value = """
                SELECT c.* FROM communities c
                JOIN users owner ON owner.id = c.owner_id
                WHERE to_tsvector('russian', c.name || ' ' || coalesce(c.description, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND (:userId IS NULL OR owner.uuid <> :userId)
                  AND (:userId IS NULL OR NOT EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  ))
                ORDER BY ts_rank(
                    to_tsvector('russian', c.name || ' ' || coalesce(c.description, '')),
                    plainto_tsquery('russian', :query)
                ) DESC
                """,
            countQuery = """
                SELECT count(*) FROM communities c
                JOIN users owner ON owner.id = c.owner_id
                WHERE to_tsvector('russian', c.name || ' ' || coalesce(c.description, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND (:userId IS NULL OR owner.uuid <> :userId)
                  AND (:userId IS NULL OR NOT EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  ))
                """,
            nativeQuery = true
    )
    Page<Community> searchFullText(
            @Param("query") String query,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет сообщества по имени пользователя (username) без учёта регистра,
     * совпадением по вхождению подстроки.
     * <p>
     * Если передан {@code userId}, из результата исключаются сообщества, на которые пользователь
     * уже подписан, и сообщества, которыми он владеет. Сортировка, переданная в {@code pageable},
     * игнорируется — порядок всегда определяется именем пользователя.
     * </p>
     *
     * @param username часть имени пользователя сообщества
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по имени пользователя
     */
    @Query(
            value = """
                SELECT c.* FROM communities c
                JOIN users owner ON owner.id = c.owner_id
                WHERE c.username ILIKE '%' || :username || '%'
                  AND (:userId IS NULL OR owner.uuid <> :userId)
                  AND (:userId IS NULL OR NOT EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  ))
                ORDER BY c.username
                """,
            countQuery = """
                SELECT count(*) FROM communities c
                JOIN users owner ON owner.id = c.owner_id
                WHERE c.username ILIKE '%' || :username || '%'
                  AND (:userId IS NULL OR owner.uuid <> :userId)
                  AND (:userId IS NULL OR NOT EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  ))
                """,
            nativeQuery = true
    )
    Page<Community> searchByUsername(
            @Param("username") String username,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет полнотекстовым поиском по названию и описанию среди сообществ, на которые подписан
     * пользователь, результаты сортируются по релевантности ({@code ts_rank}).
     * <p>
     * Использует функциональный GIN-индекс {@code communities_search_idx} (см. {@code schema.sql}).
     * Сортировка, переданная в {@code pageable}, игнорируется — порядок всегда определяется
     * релевантностью запросу.
     * </p>
     *
     * @param query поисковый запрос пользователя
     * @param userId UUID пользователя, среди подписок которого идёт поиск
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по релевантности
     */
    @Query(
            value = """
                SELECT c.* FROM communities c
                WHERE to_tsvector('russian', c.name || ' ' || coalesce(c.description, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  )
                ORDER BY ts_rank(
                    to_tsvector('russian', c.name || ' ' || coalesce(c.description, '')),
                    plainto_tsquery('russian', :query)
                ) DESC
                """,
            countQuery = """
                SELECT count(*) FROM communities c
                WHERE to_tsvector('russian', c.name || ' ' || coalesce(c.description, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  )
                """,
            nativeQuery = true
    )
    Page<Community> searchSubscribedFullText(
            @Param("query") String query,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет по имени пользователя (username) без учёта регистра среди сообществ, на которые
     * подписан пользователь, совпадением по вхождению подстроки.
     * <p>
     * Сортировка, переданная в {@code pageable}, игнорируется — порядок всегда определяется
     * именем пользователя.
     * </p>
     *
     * @param username часть имени пользователя сообщества
     * @param userId UUID пользователя, среди подписок которого идёт поиск
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по имени пользователя
     */
    @Query(
            value = """
                SELECT c.* FROM communities c
                WHERE c.username ILIKE '%' || :username || '%'
                  AND EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  )
                ORDER BY c.username
                """,
            countQuery = """
                SELECT count(*) FROM communities c
                WHERE c.username ILIKE '%' || :username || '%'
                  AND EXISTS (
                      SELECT 1 FROM community_subscription cs
                      JOIN users u ON u.id = cs.follower_id
                      WHERE cs.following_id = c.id AND u.uuid = :userId AND cs.active = true
                  )
                """,
            nativeQuery = true
    )
    Page<Community> searchSubscribedByUsername(
            @Param("username") String username,
            @Param("userId") UUID userId,
            Pageable pageable
    );

    /**
     * Ищет сообщество по уникальному идентификатору (UUID).
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденное сообщество, если оно существует
     */
    Optional<Community> findByUuid(UUID uuid);

    /**
     * Ищет сообщества, владельцем которых является пользователь с указанным UUID.
     *
     * @param ownerUuid UUID пользователя-владельца
     * @param pageable параметры пагинации
     * @return страница с сообществами, принадлежащими пользователю
     */
    Page<Community> findAllByOwner_Uuid(UUID ownerUuid, Pageable pageable);

    /**
     * Ищет сообщество по UUID и блокирует найденную строку до конца транзакции,
     * чтобы конкурентные подписки на одно и то же сообщество выполнялись по очереди.
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденное сообщество, если оно существует
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Community> findWithLockByUuid(UUID uuid);

    /**
     * Проверяет, занято ли имя пользователя сообщества.
     *
     * @param username имя пользователя сообщества
     * @return {@code true}, если сообщество с таким именем уже существует
     */
    boolean existsByUsername(String username);

    /**
     * Увеличивает счётчик подписчиков сообщества на единицу.
     *
     * @param id внутренний идентификатор сообщества
     */
    @Modifying
    // UPDATE communities SET subscribers_count = subscribers_count + 1 WHERE id =
    @Query("UPDATE Community c SET c.subscribersCount = c.subscribersCount + 1 WHERE c.id = :id")
    void incrementSubscribersCount(Long id);

    /**
     * Уменьшает счётчик подписчиков сообщества на единицу.
     *
     * @param id внутренний идентификатор сообщества
     */
    @Modifying
    // UPDATE communities SET subscribers_count = subscribers_count - 1 WHERE id =
    @Query("UPDATE Community c SET c.subscribersCount = c.subscribersCount - 1 WHERE c.id = :id")
    void decrementSubscribersCount(Long id);
}
