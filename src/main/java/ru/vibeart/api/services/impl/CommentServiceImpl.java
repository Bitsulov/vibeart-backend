package ru.vibeart.api.services.impl;

import org.hibernate.service.spi.ServiceException;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.vibeart.api.dtos.comment.CommentCreateRequest;
import ru.vibeart.api.dtos.comment.CommentResponse;
import ru.vibeart.api.dtos.comment.CommentUpdateRequest;
import ru.vibeart.api.dtos.user.UserResponse;
import ru.vibeart.api.exceptions.ForbiddenException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.exceptions.UnauthorizedException;
import ru.vibeart.api.models.entities.Comment;
import ru.vibeart.api.models.entities.Post;
import ru.vibeart.api.models.entities.User;
import ru.vibeart.api.repositories.CommentRepository;
import ru.vibeart.api.repositories.PostRepository;
import ru.vibeart.api.repositories.UserRepository;
import ru.vibeart.api.services.CommentService;
import ru.vibeart.api.utils.AuthUtil;

import java.time.Instant;
import java.util.UUID;

/**
 * Реализация {@link CommentService}.
 * <p>
 * Использует {@link ModelMapper} для преобразования сущности {@link Comment} в DTO
 * и {@link AuthUtil} для определения текущего аутентифицированного пользователя.
 * </p>
 */
@Service
public class CommentServiceImpl implements CommentService {
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;

    private static final Logger log = LoggerFactory.getLogger(CommentServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param commentRepository репозиторий комментариев
     * @param postRepository репозиторий публикаций
     * @param userRepository репозиторий пользователей
     * @param modelMapper конвертер для преобразования DTO и сущностей
     * @param authUtil утилита для получения данных текущего аутентифицированного пользователя
     */
    public CommentServiceImpl(
            CommentRepository commentRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            ModelMapper modelMapper,
            AuthUtil authUtil
    ) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
    }

    /**
     * <h1>Получение списка комментариев публикации</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает постраничный список комментариев публикации вместе с данными их авторов.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если публикация не найдена, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param postUuid UUID публикации
     * @param pageable параметры пагинации
     * @return страница с данными комментариев публикации
     * @throws ResourceNotFoundException если публикация не найдена
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentsByPost(UUID postUuid, Pageable pageable) {
        try {
            postRepository.findByUuid(postUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found with UUID: " + postUuid));

            return commentRepository.findAllByPostUuid(postUuid, pageable)
                    .map(comment -> {
                        CommentResponse response = modelMapper.map(comment, CommentResponse.class);
                        response.setAuthor(modelMapper.map(comment.getAuthor(), UserResponse.class));
                        return response;
                    });
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting comments, post UUID={}", postUuid, ex);
            throw new ServiceException("Database error getting comments", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting comments, post UUID={}", postUuid, ex);
            throw new ServiceException("Unexpected error getting comments", ex);
        }
    }

    /**
     * <h1>Создание комментария</h1>
     *
     * <h2>Назначение</h2>
     * <p>Создаёт комментарий к публикации от имени текущего пользователя и увеличивает
     * денормализованный счётчик {@code commentsCount} публикации.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если текущий пользователь или публикация не найдены, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param commentCreateRequest объект с данными нового комментария
     * @return объект с данными созданного комментария
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если текущий пользователь или публикация не найдены
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public CommentResponse createComment(CommentCreateRequest commentCreateRequest) {
        UUID authorId = authUtil.getPrincipalUuid();

        try {
            User author = userRepository.findByUuid(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));
            Post post = postRepository.findByUuid(commentCreateRequest.getPostUuid())
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            Comment comment = modelMapper.map(commentCreateRequest, Comment.class);
            comment.setUuid(UUID.randomUUID());
            comment.setPost(post);
            comment.setAuthor(author);
            comment.setCreatedAt(Instant.now());

            commentRepository.save(comment);
            postRepository.incrementCommentsCount(post.getId());

            CommentResponse response = modelMapper.map(comment, CommentResponse.class);
            response.setAuthor(modelMapper.map(author, UserResponse.class));
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during creating comment, user UUID={}", authorId, ex);
            throw new ServiceException("Database error during creating comment", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating comment, user UUID={}", authorId, ex);
            throw new ServiceException("Unexpected error during creating comment", ex);
        }
    }

    /**
     * <h1>Изменение комментария по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>Находит комментарий по UUID, проверяет, что запрос отправлен его автором, изменяет текст и возвращает.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если комментарий не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором комментария, выбрасывается {@link ForbiddenException}
     *         с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID комментария
     * @param commentUpdateRequest объект с новыми данными комментария
     * @return объект с обновлёнными данными комментария
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если комментарий не найден
     * @throws ForbiddenException если запрос отправлен не автором комментария
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public CommentResponse updateComment(UUID id, CommentUpdateRequest commentUpdateRequest) {
        UUID authorId = authUtil.getPrincipalUuid();

        try {
            Comment comment = commentRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

            if(!comment.getAuthor().getUuid().equals(authorId)) {
                log.warn("Update comment warn: client is not author, comment UUID={}; client UUID={}", id, authorId);
                throw new ForbiddenException("You cannot edit this comment");
            }

            comment.setText(commentUpdateRequest.getText());
            commentRepository.save(comment);

            CommentResponse response = modelMapper.map(comment, CommentResponse.class);
            response.setAuthor(modelMapper.map(comment.getAuthor(), UserResponse.class));
            return response;
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during updating comment, comment UUID={}", id, ex);
            throw new ServiceException("Database error during updating comment", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating comment, comment UUID={}", id, ex);
            throw new ServiceException("Unexpected error during updating comment", ex);
        }
    }

    /**
     * <h1>Удаление комментария по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>Находит комментарий по UUID, проверяет, что запрос отправлен его автором, удаляет
     * его и уменьшает денормализованный счётчик {@code commentsCount} публикации.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если комментарий не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором комментария, выбрасывается {@link ForbiddenException}
     *         с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID комментария
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если комментарий не найден
     * @throws ForbiddenException если запрос отправлен не автором комментария
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void deleteComment(UUID id) {
        UUID authorId = authUtil.getPrincipalUuid();

        try {
            Comment comment = commentRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));

            if(!comment.getAuthor().getUuid().equals(authorId)) {
                log.warn("Delete comment warn: client is not author, comment UUID={}; client UUID={}", id, authorId);
                throw new ForbiddenException("You cannot delete this comment");
            }

            commentRepository.delete(comment);
            postRepository.decrementCommentsCount(comment.getPost().getId());
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting comment, comment UUID={}", id, ex);
            throw new ServiceException("Database error during deleting comment", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting comment, comment UUID={}", id, ex);
            throw new ServiceException("Unexpected error during deleting comment", ex);
        }
    }
}
