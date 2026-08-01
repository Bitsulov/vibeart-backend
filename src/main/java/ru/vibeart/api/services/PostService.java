package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.post.PostCreateDetails;
import ru.vibeart.api.dtos.post.PostResponse;
import ru.vibeart.api.dtos.post.PostUpdateDetails;

import java.util.List;
import java.util.UUID;

/**
 * Сервис для работы с публикациями.
 */
public interface PostService {
    /**
     * Возвращает список публикаций, при необходимости отфильтрованных по альбому.
     *
     * @param albumId UUID альбома для фильтрации публикаций, или {@code null} для получения всех публикаций
     * @param pageable параметры пагинации
     * @return список объектов с данными публикаций
     */
    Page<PostResponse> getPosts(UUID albumId, Pageable pageable);

    /**
     * Возвращает список публикаций автора (пользователя или сообщества), при необходимости
     * исключая публикации, входящие в указанный альбом.
     *
     * @param authorUuid UUID автора публикаций (пользователя или сообщества)
     * @param albumId UUID альбома, публикации которого нужно исключить из результата,
     *                       или {@code null}, чтобы не исключать
     * @param pageable параметры пагинации
     * @return страница с данными публикаций автора
     */
    Page<PostResponse> getPostsByAuthor(UUID authorUuid, UUID albumId, Pageable pageable);

    /**
     * Ищет публикации полнотекстовым поиском по заголовку и описанию, результаты
     * отсортированы по релевантности запросу. Сортировка, переданная в {@code pageable},
     * не применяется — используются только номер страницы и размер.
     *
     * @param query поисковый запрос
     * @param pageable параметры пагинации (номер страницы и размер; сортировка не используется)
     * @return страница с найденными публикациями, отсортированными по релевантности
     */
    Page<PostResponse> getPostsBySearch(String query, Pageable pageable);

    /**
     * Возвращает публикацию по её UUID.
     *
     * @param uuid UUID публикации
     * @return объект с данными публикации
     */
    PostResponse getPostByUuid(UUID uuid);

    /**
     * Создаёт публикацию от имени автора.
     *
     * @param postCreateDetails объект с данными новой публикации
     * @param file изображение публикации
     * @return объект с данными созданной публикации
     */
    PostResponse createPost(PostCreateDetails postCreateDetails, MultipartFile file);

    /**
     * Изменяет данные публикации от имени автора.
     *
     * @param id UUID публикации
     * @param postUpdateDetails объект с новыми данными публикации
     * @param file новое изображение публикации
     * @return объект с данными публикации
     */
    PostResponse updatePost(UUID id, PostUpdateDetails postUpdateDetails, MultipartFile file);

    /**
     * Ставит или снимает лайк пользователя на публикации.
     *
     * @param postId UUID публикации
     */
    void toggleLike(UUID postId);

    /**
     * Увеличивает счётчик жалоб публикации на единицу.
     *
     * @param postId UUID публикации
     */
    void report(UUID postId);

    /**
     * Удаляет публикацию по UUID.
     *
     * @param id UUID публикации
     */
    void deletePostByUuid(UUID id);
}
