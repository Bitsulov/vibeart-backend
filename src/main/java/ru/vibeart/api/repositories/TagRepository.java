package ru.vibeart.api.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.vibeart.api.models.entities.Tag;

import java.util.Optional;

/**
 * Репозиторий для работы с сущностью {@link Tag}.
 * <p>
 * Расширяет {@link JpaRepository}, предоставляя стандартные CRUD-операции
 * (создание, чтение, обновление, удаление) и добавляет методы для поиска тегов.
 * </p>
 *
 * <h2>Назначение</h2>
 * <p>
 * Отвечает за доступ к данным тегов публикаций в базе данных.
 * </p>
 *
 * <h2>Основные возможности</h2>
 * <ul>
 *   <li>{@link #findByTitle(String)} — поиск тега по названию;</li>
 *   <li>{@link #findByTitleAndEnabledTrue(String)} — поиск активных тега по названию;</li>
 *   <li>{@link #findAllByEnabledTrue(Pageable)} — постраничный список активных тегов;</li>
 *   <li>{@link #findByEnabledTrueAndTitleContainingIgnoreCase(String, Pageable)} — поиск
 *       активных тегов по подстроке названия.</li>
 * </ul>
 *
 */
public interface TagRepository extends JpaRepository<Tag, Long> {
    /**
     * Ищет тег по названию.
     *
     * @param title название тега
     * @return {@link Optional}, содержащий найденный тег, если он существует
     */
    Optional<Tag> findByTitle(String title);

    /**
     * Ищет активный тег по названию.
     *
     * @param title название тега
     * @return {@link Optional}, содержащий найденный тег, если он существует и активен
     */
    Optional<Tag> findByTitleAndEnabledTrue(String title);

    /**
     * Возвращает постраничный список активных тегов.
     *
     * @param pageable параметры пагинации
     * @return страница с включёнными тегами
     */
    Page<Tag> findAllByEnabledTrue(Pageable pageable);

    /**
     * Ищет активные теги, название которых содержит переданную подстроку, без учёта регистра.
     *
     * @param query поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с найденными активными тегами
     */
    Page<Tag> findByEnabledTrueAndTitleContainingIgnoreCase(String query, Pageable pageable);
}