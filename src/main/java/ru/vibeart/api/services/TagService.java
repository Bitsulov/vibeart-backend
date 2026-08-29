package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.vibeart.api.dtos.tag.TagCreateRequest;
import ru.vibeart.api.dtos.tag.TagResponse;
import ru.vibeart.api.dtos.tag.TagUpdateRequest;

/**
 * Сервис для работы с данными тегов.
 */
public interface TagService {
    /**
     * Возвращает постраничный список активных тегов.
     *
     * @param pageable параметры пагинации
     * @return страница с данными тегов
     */
    Page<TagResponse> getTags(Pageable pageable);

    /**
     * Ищет активные теги, название которых содержит переданную подстроку, без учёта регистра.
     *
     * @param query поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с найденными тегами
     */
    Page<TagResponse> searchTags(String query, Pageable pageable);

    /**
     * Создаёт тег и возвращает его.
     *
     * @param tagCreateDetails объект с данными нового тега
     * @return объект с данными созданного тега
     */
    TagResponse createTag(TagCreateRequest tagCreateDetails);

    /**
     * Находит активный тег по текущему названию, изменяет его и возвращает.
     *
     * @param title текущее название тега
     * @param tagUpdateDetails объект с новыми данными тега
     * @return объект с данными изменённого тега
     */
    TagResponse updateTag(String title, TagUpdateRequest tagUpdateDetails);

    /**
     * Находит активный тег по названию и отключает его. Уже существующие
     * ссылки на тег у публикаций и сообществ не затрагиваются.
     *
     * @param title название тега
     */
    void deleteTag(String title);
}
