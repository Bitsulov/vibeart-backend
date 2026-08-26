package ru.vibeart.api.repositories;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.vibeart.api.models.entities.User;

import java.util.Optional;
import java.util.UUID;

/**
 * Репозиторий для работы с сущностью {@link User}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет методы для поиска по уникальным полям.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным пользователей в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findByEmail(String)} — поиск пользователя по email;</li>
 *   <li>{@link #findByUsername(String)} — поиск пользователя по имени пользователя;</li>
 *   <li>{@link #findByUuid(UUID)} — поиск пользователя по UUID;</li>
 *   <li>{@link #findWithLockByUuid(UUID)} — поиск пользователя по UUID с пессимистической блокировкой строки;</li>
 *   <li>{@link #existsByEmail(String)} — проверка наличия пользователя с указанным email.</li>
 *   <li>{@link #incrementSubscribesCount(Long)} — увеличение счётчика подписок пользователя;</li>
 *   <li>{@link #decrementSubscribesCount(Long)} — уменьшение счётчика подписок пользователя;</li>
 *   <li>{@link #incrementSubscribersCount(Long)} — увеличение счётчика подписчиков пользователя;</li>
 *   <li>{@link #decrementSubscribersCount(Long)} — уменьшение счётчика подписчиков пользователя;</li>
 *   <li>{@link #findAllFriends(UUID, Pageable)} — поиск друзей пользователя (взаимная подписка);</li>
 *   <li>{@link #searchFriendsFullText(String, UUID, Pageable)} — полнотекстовый поиск среди друзей пользователя;</li>
 *   <li>{@link #searchFriendsByUsername(String, UUID, Pageable)} — поиск среди друзей пользователя по имени пользователя.</li>
 * </ul>
 *
 */
public interface UserRepository extends JpaRepository<User, Long> {
    /**
     * Ищет пользователя по адресу электронной почты.
     *
     * @param email адрес электронной почты
     * @return {@link Optional}, содержащий найденного пользователя, если он существует
     */
    Optional<User> findByEmail(String email);

    /**
     * Ищет пользователя по имени пользователя (username).
     *
     * @param username имя пользователя
     * @return {@link Optional}, содержащий найденного пользователя, если он существует
     */
    Optional<User> findByUsername(String username);

    /**
     * Ищет пользователя по уникальному идентификатору (UUID).
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденного пользователя, если он существует
     */
    Optional<User> findByUuid(UUID uuid);

    /**
     * Ищет пользователя по UUID и блокирует найденную строку до конца транзакции,
     * чтобы конкурентные подписки на одного и того же пользователя выполнялись по очереди.
     *
     * @param uuid уникальный идентификатор
     * @return {@link Optional}, содержащий найденного пользователя, если он существует
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<User> findWithLockByUuid(UUID uuid);

    /**
     * Проверяет, существует ли пользователь с указанным адресом электронной почты.
     *
     * @param email адрес электронной почты
     * @return {@code true}, если пользователь существует, иначе {@code false}
     */
    boolean existsByEmail(String email);

    /**
     * Проверяет, существует ли пользователь с указанным именем пользователя (username).
     *
     * @param username имя пользователя
     * @return {@code true}, если пользователь существует, иначе {@code false}
     */
    boolean existsByUsername(String username);

    /**
     * Увеличивает счётчик подписок пользователя на единицу.
     *
     * @param id внутренний идентификатор пользователя
     */
    @Modifying
    // UPDATE users SET subscribes_count = subscribes_count + 1 WHERE id =
    @Query("UPDATE User u SET u.subscribesCount = u.subscribesCount + 1 WHERE u.id = :id")
    void incrementSubscribesCount(Long id);

    /**
     * Уменьшает счётчик подписок пользователя на единицу.
     *
     * @param id внутренний идентификатор пользователя
     */
    @Modifying
    // UPDATE users SET subscribes_count = subscribes_count - 1 WHERE id =
    @Query("UPDATE User u SET u.subscribesCount = u.subscribesCount - 1 WHERE u.id = :id")
    void decrementSubscribesCount(Long id);

    /**
     * Увеличивает счётчик подписчиков пользователя на единицу.
     *
     * @param id внутренний идентификатор пользователя
     */
    @Modifying
    @Query("UPDATE User u SET u.subscribersCount = u.subscribersCount + 1 WHERE u.id = :id")
    void incrementSubscribersCount(Long id);

    /**
     * Уменьшает счётчик подписчиков пользователя на единицу.
     *
     * @param id внутренний идентификатор пользователя
     */
    @Modifying
    @Query("UPDATE User u SET u.subscribersCount = u.subscribersCount - 1 WHERE u.id = :id")
    void decrementSubscribersCount(Long id);

    /**
     * Ищет друзей пользователя — тех, с кем оформлена взаимная активная подписка
     *
     * @param userId UUID пользователя, чьи друзья ищутся
     * @param pageable параметры пагинации
     * @return страница с найденными друзьями
     */
    @Query(
            value = """
                SELECT u.* FROM users u
                WHERE u.uuid <> :userId
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                """,
            countQuery = """
                SELECT count(*) FROM users u
                WHERE u.uuid <> :userId
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                """,
            nativeQuery = true
    )
    Page<User> findAllFriends(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Ищет полнотекстовым поиском среди друзей пользователя по имени, результаты
     * сортируются по релевантности ({@code ts_rank}).
     * <p>
     * Использует функциональный GIN-индекс {@code users_search_idx} (см. {@code schema.sql}).
     * Другом считается пользователь со взаимной активной подпиской. Сортировка, переданная
     * в {@code pageable}, игнорируется — порядок всегда определяется релевантностью запросу.
     * </p>
     *
     * @param query поисковый запрос
     * @param userId UUID пользователя, среди друзей которого идёт поиск
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными друзьями, отсортированными по релевантности
     */
    @Query(
            value = """
                SELECT u.* FROM users u
                WHERE to_tsvector('russian', coalesce(u.name, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                ORDER BY ts_rank(
                    to_tsvector('russian', coalesce(u.name, '')),
                    plainto_tsquery('russian', :query)
                ) DESC
                """,
            countQuery = """
                SELECT count(*) FROM users u
                WHERE to_tsvector('russian', coalesce(u.name, ''))
                      @@ plainto_tsquery('russian', :query)
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                """,
            nativeQuery = true
    )
    Page<User> searchFriendsFullText(@Param("query") String query, @Param("userId") UUID userId, Pageable pageable);

    /**
     * Ищет среди друзей пользователя по имени пользователя (username) без учёта регистра,
     * совпадением по вхождению подстроки.
     * <p>
     * Другом считается пользователь со взаимной активной подпиской. Сортировка, переданная
     * в {@code pageable}, игнорируется — порядок всегда определяется именем пользователя.
     * </p>
     *
     * @param username часть имени пользователя
     * @param userId UUID пользователя, среди друзей которого идёт поиск
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными друзьями, отсортированными по имени пользователя
     */
    @Query(
            value = """
                SELECT u.* FROM users u
                WHERE u.username ILIKE '%' || :username || '%'
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                ORDER BY u.username
                """,
            countQuery = """
                SELECT count(*) FROM users u
                WHERE u.username ILIKE '%' || :username || '%'
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.follower_id
                      WHERE s.following_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                  AND EXISTS (
                      SELECT 1 FROM subscription s
                      JOIN users principal ON principal.id = s.following_id
                      WHERE s.follower_id = u.id AND principal.uuid = :userId AND s.active = true
                  )
                """,
            nativeQuery = true
    )
    Page<User> searchFriendsByUsername(@Param("username") String username, @Param("userId") UUID userId, Pageable pageable);
}
