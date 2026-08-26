package ru.vibeart.api.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.vibeart.api.models.entities.Community;
import ru.vibeart.api.models.entities.CommunitySubscription;
import ru.vibeart.api.models.entities.User;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link CommunitySubscription}.
 * <p>
 *     Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 *     (создание, чтение, обновление, удаление) и добавляет метод для поиска подписки
 *     по паре «подписчик — сообщество».
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 *     Отвечает за доступ к данным подписок пользователей на сообщества в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findByFollowerAndFollowing(User, Community)} — поиск подписки по паре пользователь/сообщество.</li>
 * </ul>
 *
 */
public interface CommunitySubscriptionRepository extends JpaRepository<CommunitySubscription, Long> {
    /**
     * Ищет подписку по паре «пользователь — сообщество», независимо от значения флага {@code isActive}.
     *
     * @param follower пользователь-подписчик
     * @param following сообщество
     * @return {@link Optional}, содержащий найденную подписку, если она существует
     */
    Optional<CommunitySubscription> findByFollowerAndFollowing(User follower, Community following);
}
