package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import ru.vibeart.api.dtos.comment.CommentCreateRequest;
import ru.vibeart.api.dtos.comment.CommentResponse;
import ru.vibeart.api.dtos.comment.CommentUpdateRequest;

import java.util.UUID;

/**
 * Сервис для работы с данными комментариев публикаций.
 */
public interface CommentService {
    /**
     * Возвращает постраничный список комментариев публикации.
     *
     * @param postUuid UUID публикации
     * @param pageable параметры пагинации
     * @return страница с данными комментариев
     */
    Page<CommentResponse> getCommentsByPost(UUID postUuid, Pageable pageable);

    /**
     * Создаёт комментарий от имени текущего пользователя и возвращает его.
     *
     * @param commentCreateRequest объект с данными нового комментария
     * @return объект с данными созданного комментария
     */
    CommentResponse createComment(CommentCreateRequest commentCreateRequest);

    /**
     * Находит комментарий по UUID, изменяет его текст и возвращает.
     *
     * @param id UUID комментария
     * @param commentUpdateRequest объект с новыми данными комментария
     * @return объект с данными изменённого комментария
     */
    CommentResponse updateComment(UUID id, CommentUpdateRequest commentUpdateRequest);

    /**
     * Находит комментарий по UUID и удаляет его.
     *
     * @param id UUID комментария
     */
    void deleteComment(UUID id);
}
