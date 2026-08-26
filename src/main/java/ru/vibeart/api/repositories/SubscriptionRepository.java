package ru.vibeart.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vibeart.api.models.entities.Subscription;
import ru.vibeart.api.models.entities.User;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Subscription}.
 * <p>
 *     Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 *     (создание, чтение, обновление, удаление) и добавляет метод для поиска подписки
 *     по подписчику и отслеживаемому пользователю.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 *     Отвечает за доступ к данным подписок пользователей друг на друга в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findByFollowerAndFollowing(User, User)} — поиск подписки по подписчику и отслеживаемому пользователю.</li>
 * </ul>
 *
 */
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    /**
     * Ищет подписку по подписчику и отслеживаемому пользователю, независимо от значения флага {@code isActive}.
     *
     * @param follower пользователь-подписчик
     * @param following отслеживаемый пользователь
     * @return {@link Optional}, содержащий найденную подписку, если она существует
     */
    Optional<Subscription> findByFollowerAndFollowing(User follower, User following);
}
