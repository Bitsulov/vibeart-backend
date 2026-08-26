package ru.vibeart.api.services.impl;

import org.hibernate.service.spi.ServiceException;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.post.PostCreateDetails;
import ru.vibeart.api.dtos.post.PostResponse;
import ru.vibeart.api.dtos.post.PostUpdateDetails;
import ru.vibeart.api.dtos.user.UserResponse;
import ru.vibeart.api.exceptions.ConflictException;
import ru.vibeart.api.exceptions.ForbiddenException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.exceptions.UnauthorizedException;
import ru.vibeart.api.models.entities.*;
import ru.vibeart.api.repositories.*;
import ru.vibeart.api.services.PostService;
import ru.vibeart.api.utils.AuthUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Реализация {@link PostService}.
 * <p>
 * Использует {@link ModelMapper} для преобразования сущности {@link Post} в DTO,
 * {@link ImageUploaderService} для загрузки и удаления изображений публикаций
 * и {@link AuthUtil} для определения текущего аутентифицированного пользователя.
 * </p>
 */
@Service
public class PostServiceImpl implements PostService {
    private final ModelMapper modelMapper;
    private final PostRepository postRepository;
    private final AlbumRepository albumRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final ImageUploaderService imageUploaderService;
    private final TagRepository tagRepository;
    private final LikeRepository likeRepository;
    private final ReportRepository reportRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final CommunitySubscriptionRepository communitySubscriptionRepository;
    private final AuthUtil authUtil;

    private static final Logger log = LoggerFactory.getLogger(PostServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param postRepository репозиторий публикаций
     * @param albumRepository репозиторий альбомов
     * @param userRepository репозиторий пользователей
     * @param modelMapper конвертер для преобразования DTO и сущностей
     * @param communityRepository репозиторий сообществ
     * @param imageUploaderService сервис загрузки и удаления изображений
     * @param tagRepository репозиторий тегов
     * @param likeRepository репозиторий лайков
     * @param subscriptionRepository репозиторий подписок пользователей друг на друга
     * @param communitySubscriptionRepository репозиторий подписок пользователей на сообщества
     * @param authUtil утилита для получения данных текущего аутентифицированного пользователя
     */
    public PostServiceImpl(
            PostRepository postRepository,
            AlbumRepository albumRepository,
            UserRepository userRepository,
            ModelMapper modelMapper,
            CommunityRepository communityRepository,
            ImageUploaderService imageUploaderService,
            TagRepository tagRepository,
            LikeRepository likeRepository,
            ReportRepository reportRepository,
            SubscriptionRepository subscriptionRepository,
            CommunitySubscriptionRepository communitySubscriptionRepository,
            AuthUtil authUtil
    ) {
        this.postRepository = postRepository;
        this.albumRepository = albumRepository;
        this.userRepository = userRepository;
        this.modelMapper = modelMapper;
        this.communityRepository = communityRepository;
        this.imageUploaderService = imageUploaderService;
        this.tagRepository = tagRepository;
        this.likeRepository = likeRepository;
        this.reportRepository = reportRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.communitySubscriptionRepository = communitySubscriptionRepository;
        this.authUtil = authUtil;

        modelMapper.getConfiguration().setAmbiguityIgnored(true);
        modelMapper.createTypeMap(Post.class, PostResponse.class)
                .addMappings(mapper -> mapper.skip(PostResponse::setTags));
    }

    /**
     * <h1>Получение списка публикаций</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список публикаций. Если передан {@code albumId},
     *     список фильтруется по альбому, иначе возвращаются все публикации.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если альбом с переданным UUID не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param albumId UUID альбома для фильтрации публикаций, или {@code null} для получения всех публикаций
     * @param pageable параметры пагинации
     * @return страница с данными публикаций
     * @throws ResourceNotFoundException если альбом с переданным UUID не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPosts(UUID albumId, Pageable pageable) {
        boolean isAuthenticated = authUtil.getIsAuthenticated();

        try {
            User currentUser = isAuthenticated ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid())
                            .orElseThrow(() -> new ResourceNotFoundException("Principal user not found")) :
                    null;

            if(albumId != null) {
                Album album = albumRepository.findByUuid(albumId)
                        .orElseThrow(() -> new ResourceNotFoundException("Album not found with UUID: " + albumId));

                Page<Post> posts = postRepository.findAllByAlbumsUuid(albumId, pageable);

                Set<Long> likedPostIds = isAuthenticated ?
                        new HashSet<>(likeRepository.findActiveLikedPostIds(currentUser, posts.getContent())) :
                        Set.of();
                Set<Long> reportedPostIds = isAuthenticated ?
                        new HashSet<>(reportRepository.findReportedPostIds(currentUser, posts.getContent())) :
                        Set.of();

                return posts.map(post -> {
                    PostResponse response = modelMapper.map(post, PostResponse.class);

                    User authorUser = post.getAuthorUser();
                    UserResponse authorResponse = authorUser != null ? modelMapper.map(authorUser, UserResponse.class) : null;
                    if(authorResponse != null) {
                        authorResponse.setSubscribed(
                                currentUser == null || currentUser.getUuid().equals(authorUser.getUuid()) ?
                                        null :
                                        subscriptionRepository.findByFollowerAndFollowing(currentUser, authorUser)
                                                .map(Subscription::isActive).orElse(false)
                        );
                    }
                    response.setAuthor(authorResponse);

                    Community authorCommunity = post.getAuthorCommunity();
                    CommunityResponse communityResponse = authorCommunity != null ?
                            modelMapper.map(authorCommunity, CommunityResponse.class) : null;
                    if(communityResponse != null) {
                        communityResponse.setTags(authorCommunity.getTags().stream().map(Tag::getTitle).toList());
                        communityResponse.setSubscribed(
                                currentUser == null || currentUser.getUuid().equals(authorCommunity.getOwner().getUuid()) ?
                                        null :
                                        communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, authorCommunity)
                                                .map(CommunitySubscription::isActive).orElse(false)
                        );
                    }
                    response.setCommunity(communityResponse);
                    response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
                    response.setLiked(likedPostIds.contains(post.getId()));
                    response.setReported(reportedPostIds.contains(post.getId()));
                    return response;
                });
            } else {
                Page<Post> posts = postRepository.findAll(pageable);

                Set<Long> likedPostIds = isAuthenticated ?
                        new HashSet<>(likeRepository.findActiveLikedPostIds(currentUser, posts.getContent())) :
                        Set.of();
                Set<Long> reportedPostIds = isAuthenticated ?
                        new HashSet<>(reportRepository.findReportedPostIds(currentUser, posts.getContent())) :
                        Set.of();

                return posts.map(post -> {
                    PostResponse response = modelMapper.map(post, PostResponse.class);
                    response.setAuthor(
                            post.getAuthorUser() != null ? modelMapper.map(post.getAuthorUser(), UserResponse.class) : null
                    );
                    CommunityResponse communityResponse = post.getAuthorCommunity() != null ?
                            modelMapper.map(post.getAuthorCommunity(), CommunityResponse.class) : null;
                    if(communityResponse != null) {
                        communityResponse.setTags(post.getAuthorCommunity().getTags().stream().map(Tag::getTitle).toList());
                    }
                    response.setCommunity(communityResponse);
                    response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
                    response.setLiked(likedPostIds.contains(post.getId()));
                    response.setReported(reportedPostIds.contains(post.getId()));
                    return response;
                });
            }
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting posts", ex);
            throw new ServiceException("Database error getting posts", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting posts", ex);
            throw new ServiceException("Unexpected error getting posts", ex);
        }
    }

    /**
     * <h1>Получение списка публикаций автора</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список публикаций пользователя или сообщества по UUID автора.
     *     Если передан {@code albumId}, из результата исключаются публикации, входящие в этот альбом.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если автор с переданным UUID не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если альбом с переданным {@code albumId} не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param authorUuid UUID автора публикаций (пользователя или сообщества)
     * @param albumId UUID альбома, публикации которого нужно исключить из результата, или {@code null}
     * @param pageable параметры пагинации
     * @return страница с данными публикаций автора
     * @throws ResourceNotFoundException если автор или альбом не найдены
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsByAuthor(UUID authorUuid, UUID albumId, Pageable pageable) {
        boolean isAuthenticated = authUtil.getIsAuthenticated();

        try {
            User currentUser = isAuthenticated ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid())
                            .orElseThrow(() -> new ResourceNotFoundException("Principal user not found")) :
                    null;

            Optional<User> author = userRepository.findByUuid(authorUuid);
            Optional<Community> community = communityRepository.findByUuid(authorUuid);
            boolean isUser = author.isPresent();
            boolean isCommunity = community.isPresent();

            if(isUser && isCommunity) {
                log.error("Getting posts by author error: author UUID belongs to user and community, UUID={}", authorUuid);
                throw new ServiceException("Author of posts not found");
            } else if(!isUser && !isCommunity) {
                log.warn("Getting posts by author warn: author not found, UUID={}", authorUuid);
                throw new ResourceNotFoundException("Author of posts not found with UUID: " + authorUuid);
            }

            if(albumId != null) {
                albumRepository.findByUuid(albumId)
                        .orElseThrow(() -> new ResourceNotFoundException("Album not found with UUID: " + albumId));
            }

            Page<Post> posts = isUser ?
                    (albumId != null ?
                            postRepository.findAllByAuthorUserUuidExcludingAlbum(authorUuid, albumId, pageable) :
                            postRepository.findAllByAuthorUserUuid(authorUuid, pageable)) :
                    (albumId != null ?
                            postRepository.findAllByAuthorCommunityUuidExcludingAlbum(authorUuid, albumId, pageable) :
                            postRepository.findAllByAuthorCommunityUuid(authorUuid, pageable));

            Set<Long> likedPostIds = isAuthenticated ?
                    new HashSet<>(likeRepository.findActiveLikedPostIds(currentUser, posts.getContent())) :
                    Set.of();
            Set<Long> reportedPostIds = isAuthenticated ?
                    new HashSet<>(reportRepository.findReportedPostIds(currentUser, posts.getContent())) :
                    Set.of();

            return posts.map(post -> {
                PostResponse response = modelMapper.map(post, PostResponse.class);

                User authorUser = post.getAuthorUser();
                UserResponse authorResponse = authorUser != null ? modelMapper.map(authorUser, UserResponse.class) : null;
                if(authorResponse != null) {
                    authorResponse.setSubscribed(
                            currentUser == null || currentUser.getUuid().equals(authorUser.getUuid()) ?
                                    null :
                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, authorUser)
                                            .map(Subscription::isActive).orElse(false)
                    );
                }
                response.setAuthor(authorResponse);

                Community authorCommunity = post.getAuthorCommunity();
                CommunityResponse communityResponse = authorCommunity != null ?
                        modelMapper.map(authorCommunity, CommunityResponse.class) : null;
                if(communityResponse != null) {
                    communityResponse.setTags(authorCommunity.getTags().stream().map(Tag::getTitle).toList());
                    communityResponse.setSubscribed(
                            currentUser == null || currentUser.getUuid().equals(authorCommunity.getOwner().getUuid()) ?
                                    null :
                                    communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, authorCommunity)
                                            .map(CommunitySubscription::isActive).orElse(false)
                    );
                }
                response.setCommunity(communityResponse);
                response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
                response.setLiked(likedPostIds.contains(post.getId()));
                response.setReported(reportedPostIds.contains(post.getId()));
                return response;
            });
        } catch (ResourceNotFoundException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting posts by author, author UUID={}", authorUuid, ex);
            throw new ServiceException("Database error getting posts by author", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting posts by author, author UUID={}", authorUuid, ex);
            throw new ServiceException("Unexpected error getting posts by author", ex);
        }
    }

    /**
     * <h1>Полнотекстовый поиск публикаций</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Ищет публикации по заголовку и описанию через полнотекстовый поиск PostgreSQL,
     *     результаты отсортированы по релевантности запросу. Сортировка, переданная в {@code pageable},
     *     не используется - из него берутся только номер страницы и размер.
     * </p>
     * <p>
     *     Если передан {@code authorUuid}, поиск ограничивается публикациями указанного автора
     *     ({@link PostRepository#searchFullTextByAuthorUserUuid(String, UUID, UUID, Pageable)} или
     *     {@link PostRepository#searchFullTextByAuthorCommunityUuid(String, UUID, UUID, Pageable)});
     *     если дополнительно передан {@code albumId}, публикации, входящие в этот альбом, исключаются
     *     из результата. Без {@code authorUuid} используется
     *     {@link PostRepository#searchFullText(String, Pageable)}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если аутентифицированный пользователь не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если автор с переданным {@code authorUuid} не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если альбом с переданным {@code albumId} не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param query поисковый запрос
     * @param authorUuid UUID автора публикаций, или {@code null}
     * @param albumId UUID альбома, публикации которого нужно исключить из результата, или {@code null}
     * @param pageable параметры пагинации (номер страницы и размер; сортировка не используется)
     * @return страница с найденными публикациями, отсортированными по релевантности
     * @throws ResourceNotFoundException если аутентифицированный пользователь, автор или альбом не найдены
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<PostResponse> getPostsBySearch(String query, UUID authorUuid, UUID albumId, Pageable pageable) {
        boolean isAuthenticated = authUtil.getIsAuthenticated();

        try {
            User currentUser = isAuthenticated ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid())
                            .orElseThrow(() -> new ResourceNotFoundException("Principal user not found")) :
                    null;

            Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

            Page<Post> posts;
            if(authorUuid != null) {
                Optional<User> author = userRepository.findByUuid(authorUuid);
                Optional<Community> community = communityRepository.findByUuid(authorUuid);
                boolean isUser = author.isPresent();
                boolean isCommunity = community.isPresent();

                if(isUser && isCommunity) {
                    log.error("Searching posts by author error: author UUID belongs to user and community, UUID={}", authorUuid);
                    throw new ServiceException("Author of posts not found");
                } else if(!isUser && !isCommunity) {
                    log.warn("Searching posts by author warn: author not found, UUID={}", authorUuid);
                    throw new ResourceNotFoundException("Author of posts not found with UUID: " + authorUuid);
                }

                if(albumId != null) {
                    albumRepository.findByUuid(albumId)
                            .orElseThrow(() -> new ResourceNotFoundException("Album not found with UUID: " + albumId));
                }

                posts = isUser ?
                        postRepository.searchFullTextByAuthorUserUuid(query, authorUuid, albumId, unsortedPageable) :
                        postRepository.searchFullTextByAuthorCommunityUuid(query, authorUuid, albumId, unsortedPageable);
            } else {
                posts = postRepository.searchFullText(query, unsortedPageable);
            }

            Set<Long> likedPostIds = isAuthenticated ?
                    new HashSet<>(likeRepository.findActiveLikedPostIds(currentUser, posts.getContent())) :
                    Set.of();
            Set<Long> reportedPostIds = isAuthenticated ?
                    new HashSet<>(reportRepository.findReportedPostIds(currentUser, posts.getContent())) :
                    Set.of();

            return posts.map(post -> {
                PostResponse response = modelMapper.map(post, PostResponse.class);

                User authorUser = post.getAuthorUser();
                UserResponse authorResponse = authorUser != null ? modelMapper.map(authorUser, UserResponse.class) : null;
                if(authorResponse != null) {
                    authorResponse.setSubscribed(
                            currentUser == null || currentUser.getUuid().equals(authorUser.getUuid()) ?
                                    null :
                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, authorUser)
                                            .map(Subscription::isActive).orElse(false)
                    );
                }
                response.setAuthor(authorResponse);

                Community authorCommunity = post.getAuthorCommunity();
                CommunityResponse communityResponse = authorCommunity != null ?
                        modelMapper.map(authorCommunity, CommunityResponse.class) : null;
                if(communityResponse != null) {
                    communityResponse.setTags(authorCommunity.getTags().stream().map(Tag::getTitle).toList());
                    communityResponse.setSubscribed(
                            currentUser == null || currentUser.getUuid().equals(authorCommunity.getOwner().getUuid()) ?
                                    null :
                                    communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, authorCommunity)
                                            .map(CommunitySubscription::isActive).orElse(false)
                    );
                }
                response.setCommunity(communityResponse);
                response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
                response.setLiked(likedPostIds.contains(post.getId()));
                response.setReported(reportedPostIds.contains(post.getId()));
                return response;
            });
        } catch (ResourceNotFoundException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during searching posts, query={}", query, ex);
            throw new ServiceException("Database error searching posts", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during searching posts, query={}", query, ex);
            throw new ServiceException("Unexpected error searching posts", ex);
        }
    }

    /**
     * <h1>Получение публикации по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает данные публикации по её UUID вместе со списком названий тегов.</p>
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
     * @param postId UUID публикации
     * @return объект с данными публикации
     * @throws ResourceNotFoundException если публикация не найдена
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public PostResponse getPostByUuid(UUID postId) {
        boolean isAuthenticated = authUtil.getIsAuthenticated();

        try {
            Post post = postRepository.findByUuid(postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found with UUID: " + postId));

            User currentUser = isAuthenticated ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid())
                            .orElseThrow(() -> new ResourceNotFoundException("Principal user not found")) :
                    null;

            PostResponse response = modelMapper.map(post, PostResponse.class);

            User authorUser = post.getAuthorUser();
            UserResponse authorResponse = authorUser != null ? modelMapper.map(authorUser, UserResponse.class) : null;
            if(authorResponse != null) {
                authorResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(authorUser.getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, authorUser)
                                        .map(Subscription::isActive).orElse(false)
                );
            }
            response.setAuthor(authorResponse);

            Community authorCommunity = post.getAuthorCommunity();
            CommunityResponse communityResponse = authorCommunity != null ?
                    modelMapper.map(authorCommunity, CommunityResponse.class) : null;
            if(communityResponse != null) {
                communityResponse.setTags(authorCommunity.getTags().stream().map(Tag::getTitle).toList());
                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(authorCommunity.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, authorCommunity)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
            }
            response.setCommunity(communityResponse);

            if(currentUser != null) {
                likeRepository.findByUserAndPost(currentUser, post)
                        .ifPresentOrElse(
                                (l) -> response.setLiked(l.isActive()),
                                () -> response.setLiked(false)
                        );
                response.setReported(reportRepository.existsByUserAndPost(currentUser, post));
            }

            response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting post with UUID={}", postId, ex);
            throw new ServiceException("Database error during getting post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting post with UUID={}", postId, ex);
            throw new ServiceException("Unexpected error during getting post", ex);
        }
    }

    /**
     * <h1>Создание публикации от имени автора</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Создаёт публикацию от имени пользователя или сообщества, загружает изображение через
     *     {@link ImageUploaderService} и привязывает теги по названиям через {@link TagRepository}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если автор публикации не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если любой из переданных тегов не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь пытается создать пост от имени другого пользователя, либо от имени
     *         сообщества, владельцем или администратором которого он не является, выбрасывается
     *         {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке чтения файла изображения ({@link IOException}),
     *         ошибке загрузки в хранилище или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param postCreateDetails объект с данными новой публикации
     * @param file изображение публикации
     * @return объект с данными созданной публикации
     * @throws ResourceNotFoundException если автор или один из тегов не найден
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ForbiddenException если пользователь не вправе создать пост от имени указанного автора
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public PostResponse createPost(PostCreateDetails postCreateDetails, MultipartFile file) {
        UUID authorId = authUtil.getPrincipalUuid();

        try {
            Post post = modelMapper.map(postCreateDetails, Post.class);
            UUID postAuthorUuid = postCreateDetails.getAuthorUuid();

            Optional<User> user = userRepository.findByUuid(postAuthorUuid);
            Optional<Community> community = communityRepository.findByUuid(postAuthorUuid);
            boolean isUser = user.isPresent();
            boolean isCommunity = community.isPresent();

            if(isUser && isCommunity) {
                log.error("Creating post error: author UUID belongs to user and community, UUID={}", postAuthorUuid);
                throw new ServiceException("Author of post not found");
            } else if(!isUser && !isCommunity) {
                log.warn("Creating post warn: author not found, passed UUID={}", postAuthorUuid);
                throw new ResourceNotFoundException("Author of post not found");
            } else if(isUser && !user.get().getUuid().equals(authorId)) {
                log.warn("Create post warn: client is not author, client UUID={}", authorId);
                throw new ForbiddenException("You cannot create a post as another user");
            } else if(isCommunity) {
                boolean isOwnerOrAdmin = community.get().getOwner().getUuid().equals(authorId) ||
                        community.get().getAdmins().stream().anyMatch(admin -> admin.getUuid().equals(authorId));

                if(!isOwnerOrAdmin) {
                    log.warn("Create post warn: client is not community owner or admin, client UUID={}", authorId);
                    throw new ForbiddenException("You cannot create a post as this community");
                }
            }

            User currentUser = userRepository.findByUuid(authorId)
                    .orElseThrow(() -> new ServiceException("Principal user not found"));

            post.setAuthorUser(user.orElse(null));
            post.setAuthorCommunity(community.orElse(null));

            String imageUrl = imageUploaderService.uploadImage(file);
            post.setImageUrl(imageUrl);
            post.setCreatedAt(Instant.now());
            post.setUuid(UUID.randomUUID());

            List<Tag> tags = new ArrayList<>(postCreateDetails.getTagsTitles().stream()
                    .map(tagTitle ->
                            tagRepository.findByTitle(tagTitle)
                                    .orElseThrow(() -> {
                                        log.warn("Create post warn: Tag {} not found, post UUID={}", tagTitle, post.getUuid());
                                        return new ResourceNotFoundException("Tag " + tagTitle + " not found");
                                    })
                    )
                    .toList());
            post.setTags(tags);

            postRepository.save(post);

            PostResponse response = modelMapper.map(post, PostResponse.class);

            User authorUser = post.getAuthorUser();
            UserResponse authorResponse = authorUser != null ? modelMapper.map(authorUser, UserResponse.class) : null;
            if(authorResponse != null) {
                authorResponse.setSubscribed(
                        currentUser.getUuid().equals(authorUser.getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, authorUser)
                                        .map(Subscription::isActive).orElse(false)
                );
            }
            response.setAuthor(authorResponse);

            Community authorCommunity = post.getAuthorCommunity();
            CommunityResponse communityResponse = authorCommunity != null ?
                    modelMapper.map(authorCommunity, CommunityResponse.class) : null;
            if(communityResponse != null) {
                communityResponse.setTags(authorCommunity.getTags().stream().map(Tag::getTitle).toList());
                communityResponse.setSubscribed(
                        currentUser.getUuid().equals(authorCommunity.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, authorCommunity)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
            }
            response.setCommunity(communityResponse);
            response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
            return response;
        } catch (ResourceNotFoundException | ForbiddenException | ServiceException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Image load error during creating post, user UUID={}", authorId, ex);
            throw new ServiceException("File loading error during creating post", ex);
        } catch (DataAccessException ex) {
            log.error("Database error during creating post, user UUID={}", authorId, ex);
            throw new ServiceException("Database error during creating post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating post, user UUID={}", authorId, ex);
            throw new ServiceException("Unexpected error during creating post", ex);
        }
    }

    /**
     * <h1>Изменение публикации по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит публикацию по UUID, проверяет, что запрос отправлен её автором
     *     (пользователем или сообществом), при наличии нового файла удаляет старое изображение
     *     и загружает новое через {@link ImageUploaderService}, а теги, если переданы,
     *     полностью заменяет на найденные по названиям через {@link TagRepository}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если автор, публикация или один из переданных тегов не найден,
     *         выбрасывается {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором публикации, выбрасывается {@link ForbiddenException}
     *         с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке чтения файла изображения,
     *         ошибке загрузки в хранилище или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID публикации
     * @param postUpdateDetails объект с новыми данными публикации
     * @param file новое изображение публикации, или {@code null}, если не заменяется
     * @return объект с обновлёнными данными публикации
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если автор, публикация или один из тегов не найден
     * @throws ForbiddenException если запрос отправлен не автором публикации
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public PostResponse updatePost(UUID id, PostUpdateDetails postUpdateDetails, MultipartFile file) {
        UUID authorId = authUtil.getPrincipalUuid();

        try {
            userRepository.findByUuid(authorId)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));

            Post post = postRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            boolean canEditPost =
                    (post.getAuthorUser() != null && post.getAuthorUser().getUuid().equals(authorId)) ||
                    (post.getAuthorCommunity() != null && (
                            post.getAuthorCommunity().getOwner().getUuid().equals(authorId) ||
                            post.getAuthorCommunity().getAdmins().stream().anyMatch(admin -> admin.getUuid().equals(authorId))
                    ));

            if(!canEditPost) {
                log.warn("Update post warn: client is not author, post UUID={}; client UUID={}", id, authorId);
                throw new ForbiddenException("You cannot edit this post");
            }

            if(file != null) {
                String oldImageUrl = post.getImageUrl();

                if(!oldImageUrl.isEmpty()) {
                    imageUploaderService.deleteImage(oldImageUrl);
                }
                String imageUrl = imageUploaderService.uploadImage(file);
                post.setImageUrl(imageUrl);
            }

            List<Tag> tags = new ArrayList<>(postUpdateDetails.getTagsTitles().stream()
                    .map(tagTitle ->
                            tagRepository.findByTitle(tagTitle)
                                    .orElseThrow(() -> {
                                        log.warn("Update post warn: Tag {} not found, post UUID={}", tagTitle, post.getUuid());
                                        return new ResourceNotFoundException("Tag " + tagTitle + " not found");
                                    })
                    )
                    .toList());
            post.setTags(tags);

            post.setTitle(postUpdateDetails.getTitle());
            post.setDescription(postUpdateDetails.getDescription());
            postRepository.save(post);

            PostResponse response = modelMapper.map(post, PostResponse.class);
            response.setTags(post.getTags().stream().map(Tag::getTitle).toList());
            return response;
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Image load error during updating post, post UUID={}", id, ex);
            throw new ServiceException("File loading error during updating post", ex);
        } catch (DataAccessException ex) {
            log.error("Database error during updating post, post UUID={}", id, ex);
            throw new ServiceException("Database error during updating post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating post, post UUID={}", id, ex);
            throw new ServiceException("Unexpected error during updating post", ex);
        }
    }

    /**
     * <h1>Переключение лайка публикации</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Ставит или снимает лайк пользователя на публикации и обновляет денормализованный
     *     счётчик {@code likesCount}. Строка публикации блокируется через
     *     {@link PostRepository#findWithLockByUuid(UUID)}, чтобы конкурентные запросы одного
     *     и того же пользователя на один и тот же пост выполнялись по очереди, а не создавали
     *     дублирующиеся записи лайка.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь или публикация не найдены, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param postId UUID публикации
     * @throws ResourceNotFoundException если пользователь или публикация не найдены
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void toggleLike(UUID postId) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            Post post = postRepository.findWithLockByUuid(postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            Like like = likeRepository.findByUserAndPost(user, post)
                    .orElseGet(() -> new Like(false, user, post));

            if(!like.isActive()) {
                like.setActive(true);
                postRepository.incrementLikesCount(post.getId());
            } else {
                like.setActive(false);
                postRepository.decrementLikesCount(post.getId());
            }

            likeRepository.save(like);
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during liking post, user id: {}; post UUID={}", userId, postId, ex);
            throw new ServiceException("Database error during updating post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during liking post, user id: {}; post UUID={}", userId, postId, ex);
            throw new ServiceException("Unexpected error during updating post", ex);
        }
    }

    /**
     * <h1>Жалоба на публикацию</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Регистрирует жалобу текущего пользователя на публикацию и увеличивает денормализованный
     *     счётчик {@code reportsCount}. Повторная жалоба того же пользователя на тот же пост отклоняется.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если пользователь или публикация не найдены, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь уже жаловался на эту публикацию, выбрасывается {@link ConflictException}
     *         с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param postId UUID публикации
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если пользователь или публикация не найдены
     * @throws ConflictException если пользователь уже жаловался на эту публикацию
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void report(UUID postId) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));
            Post post = postRepository.findByUuid(postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            if(reportRepository.existsByUserAndPost(user, post)) {
                log.warn("Report post warn: already reported, post UUID={}; user UUID={}", postId, userId);
                throw new ConflictException("You have already reported this post");
            }

            reportRepository.save(new Report(user, post));
            postRepository.incrementReportsCount(post.getId());
        } catch (ResourceNotFoundException | ConflictException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during reporting post, user id: {}; post UUID={}", userId, postId, ex);
            throw new ServiceException("Database error during reporting post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during reporting post, user id: {}; post UUID={}", userId, postId, ex);
            throw new ServiceException("Unexpected error during reporting post", ex);
        }
    }

    /**
     * <h1>Удаление публикации по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Удаляет публикацию из базы данных вместе с её изображением в хранилище
     *     через {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если текущий пользователь или публикация не найдены, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором публикации, выбрасывается
     *         {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке удаления изображения из хранилища
     *         или любой другой ошибке, выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param postId UUID публикации
     * @throws ResourceNotFoundException если текущий пользователь или публикация не найдены
     * @throws ForbiddenException если запрос отправлен не автором публикации
     * @throws ServiceException если произошла ошибка базы данных, удаления изображения или сервера
     */
    @Override
    @Transactional
    public void deletePostByUuid(UUID postId) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            userRepository.findByUuid(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));

            Post post = postRepository.findByUuid(postId)
                    .orElseThrow(() -> new ResourceNotFoundException("Post not found"));

            boolean isOwner =
                    (post.getAuthorUser() != null && post.getAuthorUser().getUuid().equals(userId)) ||
                    (post.getAuthorCommunity() != null && post.getAuthorCommunity().getOwner().getUuid().equals(userId));

            if(!isOwner) {
                log.warn("Delete post warn: client is not author, client UUID={}", userId);
                throw new ForbiddenException("You cannot delete this post");
            }

            String imageUrl = post.getImageUrl();
            if(!imageUrl.isEmpty()) {
                imageUploaderService.deleteImage(imageUrl);
            }

            postRepository.delete(post);
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting post with UUID={}", postId, ex);
            throw new ServiceException("Database error during getting post", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting post with UUID={}", postId, ex);
            throw new ServiceException("Unexpected error during getting post", ex);
        }
    }
}
