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
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.album.AlbumCreateDetails;
import ru.vibeart.api.dtos.album.AlbumResponse;
import ru.vibeart.api.dtos.album.AlbumUpdateDetails;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.user.UserResponse;
import ru.vibeart.api.exceptions.ForbiddenException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.exceptions.UnauthorizedException;
import ru.vibeart.api.models.entities.Album;
import ru.vibeart.api.models.entities.Community;
import ru.vibeart.api.models.entities.Post;
import ru.vibeart.api.models.entities.User;
import ru.vibeart.api.repositories.AlbumRepository;
import ru.vibeart.api.repositories.CommunityRepository;
import ru.vibeart.api.repositories.PostRepository;
import ru.vibeart.api.repositories.UserRepository;
import ru.vibeart.api.services.AlbumService;
import ru.vibeart.api.utils.AuthUtil;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Реализация {@link AlbumService}.
 * <p>
 * Использует {@link ModelMapper} для преобразования сущности {@link Album} в DTO,
 * {@link ImageUploaderService} для загрузки и удаления изображений альбомов
 * и {@link AuthUtil} для определения текущего аутентифицированного пользователя.
 * </p>
 */
@Service
public class AlbumServiceImpl implements AlbumService {
    private final AlbumRepository albumRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommunityRepository communityRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ImageUploaderService imageUploaderService;

    private static final Logger log = LoggerFactory.getLogger(AlbumServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param albumRepository репозиторий альбомов
     * @param postRepository репозиторий публикаций
     * @param userRepository репозиторий пользователей
     * @param communityRepository репозиторий сообществ
     * @param modelMapper конвертер для преобразования DTO и сущностей
     * @param authUtil утилита для получения данных текущего аутентифицированного пользователя
     * @param imageUploaderService сервис загрузки и удаления изображений
     */
    public AlbumServiceImpl(
            AlbumRepository albumRepository,
            PostRepository postRepository,
            UserRepository userRepository,
            CommunityRepository communityRepository,
            ModelMapper modelMapper,
            AuthUtil authUtil,
            ImageUploaderService imageUploaderService
    ) {
        this.albumRepository = albumRepository;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.communityRepository = communityRepository;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.imageUploaderService = imageUploaderService;
    }

    /**
     * <h1>Получение списка альбомов автора</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает постраничный список альбомов пользователя или сообщества по UUID автора.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если автор с переданным UUID не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param authorUuid UUID автора альбомов (пользователя или сообщества)
     * @param pageable параметры пагинации
     * @return страница с данными альбомов
     * @throws ResourceNotFoundException если автор альбомов не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    public Page<AlbumResponse> getAlbumsByUserOrCommunity(UUID authorUuid, Pageable pageable) {
        try {
            Optional<User> user = userRepository.findByUuid(authorUuid);
            Optional<Community> community = communityRepository.findByUuid(authorUuid);

            Page<Album> albums;
            boolean isEmptyUser = user.isEmpty();
            boolean isEmptyCommunity = community.isEmpty();
            if(isEmptyUser && isEmptyCommunity) {
                throw new ResourceNotFoundException("Author of album not found");
            } else if(!isEmptyUser && !isEmptyCommunity) {
                log.error("Getting album list by user or community error: author UUID belongs to user and community, UUID={}", authorUuid);
                throw new ServiceException("Author of album not found");
            } else if(!isEmptyUser) {
                albums = albumRepository.findAllByAuthorUserUuid(authorUuid, pageable);
            } else {
                albums = albumRepository.findAllByAuthorCommunityUuid(authorUuid, pageable);
            }

            UserResponse authorUserResponse = !isEmptyUser ?
                    modelMapper.map(user.get(), UserResponse.class) : null;
            CommunityResponse authorCommunityResponse = !isEmptyCommunity ?
                    modelMapper.map(community.get(), CommunityResponse.class) : null;

            return albums.map(album -> {
                AlbumResponse response = modelMapper.map(album, AlbumResponse.class);
                response.setAuthorUser(authorUserResponse);
                response.setAuthorCommunity(authorCommunityResponse);
                return response;
            });
        } catch (ResourceNotFoundException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting albums by user or community, author UUID={}", authorUuid, ex);
            throw new ServiceException("Database error getting albums by user or community", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting albums by user or community, author UUID={}", authorUuid, ex);
            throw new ServiceException("Unexpected error getting albums by user or community", ex);
        }
    }

    /**
     * <h1>Получение альбома по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает данные альбома по его UUID.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если альбом не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param uuid UUID альбома
     * @return объект с данными альбома
     * @throws ResourceNotFoundException если альбом не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    public AlbumResponse getAlbumByUuid(UUID uuid) {
        try {
            Album album = albumRepository.findByUuid(uuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Album not found"));

            AlbumResponse response = modelMapper.map(album, AlbumResponse.class);
            response.setAuthorUser(
                    album.getAuthorUser() != null ?
                            modelMapper.map(album.getAuthorUser(), UserResponse.class) :
                            null
            );
            response.setAuthorCommunity(
                    album.getAuthorCommunity() != null ?
                            modelMapper.map(album.getAuthorCommunity(), CommunityResponse.class) :
                            null
            );
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting album by UUID={}", uuid, ex);
            throw new ServiceException("Database error getting album", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting album by UUID={}", uuid, ex);
            throw new ServiceException("Unexpected error getting album", ex);
        }
    }

    /**
     * <h1>Создание альбома</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Создаёт альбом от имени пользователя или сообщества, при наличии файла загружает
     *     изображение альбома через {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если текущий пользователь или автор альбома не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь пытается создать альбом от имени другого пользователя, либо от имени
     *         сообщества, владельцем или администратором которого он не является, выбрасывается
     *         {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке чтения файла изображения ({@link IOException}),
     *         ошибке загрузки в хранилище или любой другой ошибке, выбрасывается
     *         {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param albumCreateDetails объект с данными нового альбома
     * @param file изображение альбома
     * @return объект с данными созданного альбома
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если текущий пользователь или автор альбома не найден
     * @throws ForbiddenException если пользователь не вправе создать альбом от имени указанного автора
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public AlbumResponse createAlbum(AlbumCreateDetails albumCreateDetails, MultipartFile file) {
        UUID clientUuid = authUtil.getPrincipalUuid();
        UUID authorUuid = albumCreateDetails.getAuthorUuid();

        try {
            userRepository.findByUuid(clientUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));

            Optional<User> user = userRepository.findByUuid(authorUuid);
            Optional<Community> community = communityRepository.findByUuid(authorUuid);
            boolean isUser = user.isPresent();
            boolean isCommunity = community.isPresent();

            if(isUser && isCommunity) {
                log.error("Creating album error: client UUID belongs to user and community, UUID={}", authorUuid);
                throw new ServiceException("Principal user or community not found");
            } else if(!isUser && !isCommunity) {
                log.warn("Creating album warn: author not found, passed UUID={}", authorUuid);
                throw new ResourceNotFoundException("Author of album not found");
            } else if(isUser && !clientUuid.equals(user.get().getUuid())) {
                log.warn("Creating album warn: client doesn't have access to create album by this user, client UUID={}, passed UUID={}", clientUuid, authorUuid);
                throw new ForbiddenException("You cannot create album as this user");
            } else if(isCommunity) {
                boolean isOwnerOrAdmin = clientUuid.equals(community.get().getOwner().getUuid()) ||
                        community.get().getAdmins().stream().anyMatch(
                                admin -> clientUuid.equals(admin.getUuid())
                        );

                if(!isOwnerOrAdmin) {
                    log.warn("Creating album warn: client doesn't have access to create album by this community, client UUID={}, passed UUID={}", clientUuid, authorUuid);
                    throw new ForbiddenException("You cannot create album as this community");
                }
            }

            Album album = modelMapper.map(albumCreateDetails, Album.class);
            if(file != null && !file.isEmpty()) {
                String imageUrl = imageUploaderService.uploadImage(file);
                album.setImageUrl(imageUrl);
            }

            album.setCreatedAt(Instant.now());
            album.setUuid(UUID.randomUUID());
            album.setWorksCount(0);

            if(isUser) {
                album.setAuthorUser(user.get());
                album.setAuthorCommunity(null);
            } else if(isCommunity) {
                album.setAuthorCommunity(community.get());
                album.setAuthorUser(null);
            }

            albumRepository.save(album);

            AlbumResponse response = modelMapper.map(album, AlbumResponse.class);
            response.setAuthorUser(isUser ? modelMapper.map(user.get(), UserResponse.class) : null);
            response.setAuthorCommunity(isCommunity ? modelMapper.map(community.get(), CommunityResponse.class) : null);
            return response;
        } catch (ServiceException | ForbiddenException | ResourceNotFoundException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Image load error during creating album, client UUID={}", clientUuid, ex);
            throw new ServiceException("File loading error during creating album", ex);
        } catch (DataAccessException ex) {
            log.error("Database error during creating album, author UUID={}", authorUuid, ex);
            throw new ServiceException("Database error getting album", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating album, author UUID={}", authorUuid, ex);
            throw new ServiceException("Unexpected error getting album", ex);
        }
    }

    /**
     * <h1>Изменение альбома по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит альбом по UUID, проверяет, что запрос отправлен его автором (пользователем
     *     или сообществом), при наличии нового файла удаляет старое изображение и загружает
     *     новое через {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если текущий пользователь или альбом не найдены, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором альбома, выбрасывается {@link ForbiddenException}
     *         с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке чтения файла изображения, ошибке загрузки
     *         в хранилище или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param uuid UUID альбома
     * @param albumUpdateDetails объект с новыми данными альбома
     * @param file новое изображение альбома, или {@code null}, если не заменяется
     * @return объект с обновлёнными данными альбома
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если текущий пользователь или альбом не найдены
     * @throws ForbiddenException если запрос отправлен не автором альбома
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public AlbumResponse updateAlbum(UUID uuid, AlbumUpdateDetails albumUpdateDetails, MultipartFile file) {
        UUID clientUuid = authUtil.getPrincipalUuid();

        try {
            userRepository.findByUuid(clientUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));

            Album album = albumRepository.findByUuid(uuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Album not found"));

            User authorUser = album.getAuthorUser();
            Community authorCommunity = album.getAuthorCommunity();
            boolean isUser = authorUser != null;
            boolean isCommunity = authorCommunity != null;

            if(isUser && !authorUser.getUuid().equals(clientUuid)) {
                log.warn("Updating album warn: client doesn't have access to update album by this user, client UUID={}, album UUID={}", clientUuid, uuid);
                throw new ForbiddenException("You cannot update album as this user");
            } else if(isCommunity) {
                boolean isOwnerOrAdmin = clientUuid.equals(authorCommunity.getOwner().getUuid()) ||
                        authorCommunity.getAdmins().stream().anyMatch(
                                admin -> clientUuid.equals(admin.getUuid())
                        );

                if(!isOwnerOrAdmin) {
                    log.warn("Updating album warn: client doesn't have access to update album by this community, client UUID={}, album UUID={}", clientUuid, uuid);
                    throw new ForbiddenException("You cannot update album as this community");
                }
            }

            if(file != null && !file.isEmpty()) {
                String imageUrl = imageUploaderService.uploadImage(file);
                if(!album.getImageUrl().isEmpty()) {
                    imageUploaderService.deleteImage(album.getImageUrl());
                }
                album.setImageUrl(imageUrl);
            }
            album.setTitle(albumUpdateDetails.getTitle());
            album.setDescription(albumUpdateDetails.getDescription());
            albumRepository.save(album);

            AlbumResponse response = modelMapper.map(album, AlbumResponse.class);
            response.setAuthorUser(isUser ? modelMapper.map(authorUser, UserResponse.class) : null);
            response.setAuthorCommunity(isCommunity ? modelMapper.map(authorCommunity, CommunityResponse.class) : null);
            return response;
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Image load error during creating album, client UUID={}", clientUuid, ex);
            throw new ServiceException("File loading error during creating album", ex);
        } catch (DataAccessException ex) {
            log.error("Database error during creating album, author UUID={}", clientUuid, ex);
            throw new ServiceException("Database error getting album", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating album, author UUID={}", clientUuid, ex);
            throw new ServiceException("Unexpected error getting album", ex);
        }
    }

    /**
     * <h1>Добавление публикаций в альбом</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Добавляет публикации по списку UUID в альбом и увеличивает денормализованный
     *     счётчик {@code worksCount}. Публикации, уже входящие в альбом, повторно не добавляются.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если текущий пользователь, альбом или одна из публикаций не найдены, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором альбома, либо одна из публикаций принадлежит
     *         другому автору, выбрасывается {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param albumUuid UUID альбома
     * @param postUuids список UUID публикаций для добавления
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если текущий пользователь, альбом или одна из публикаций не найдены
     * @throws ForbiddenException если запрос отправлен не автором альбома, либо публикация принадлежит другому автору
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void addPostsToAlbum(UUID albumUuid, List<UUID> postUuids) {
        UUID clientUuid = authUtil.getPrincipalUuid();

        try {
            userRepository.findByUuid(clientUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));

            Album album = albumRepository.findByUuid(albumUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Album not found"));

            User authorUser = album.getAuthorUser();
            Community authorCommunity = album.getAuthorCommunity();
            boolean isUser = authorUser != null;
            boolean isCommunity = authorCommunity != null;

            if(isUser && !authorUser.getUuid().equals(clientUuid)) {
                log.warn("Adding posts to album warn: client doesn't have access to this album, client UUID={}, album UUID={}", clientUuid, albumUuid);
                throw new ForbiddenException("You cannot add posts to album as this user");
            } else if(isCommunity) {
                boolean isOwnerOrAdmin = clientUuid.equals(authorCommunity.getOwner().getUuid()) ||
                        authorCommunity.getAdmins().stream().anyMatch(
                                admin -> clientUuid.equals(admin.getUuid())
                        );

                if(!isOwnerOrAdmin) {
                    log.warn("Adding posts to album warn: client doesn't have access to this album, client UUID={}, album UUID={}", clientUuid, albumUuid);
                    throw new ForbiddenException("You cannot add posts to album as this community");
                }
            }

            List<Post> foundPosts = postRepository.findAllByUuidIn(postUuids);
            if(foundPosts.size() != postUuids.size()) {
                Set<UUID> foundUuids = foundPosts.stream().map(Post::getUuid).collect(Collectors.toSet());
                List<UUID> missing = postUuids.stream().filter(postUuid -> !foundUuids.contains(postUuid)).toList();
                log.warn("Adding posts to album warn: posts not found, album UUID={}, missing={}", albumUuid, missing);
                throw new ResourceNotFoundException("Posts not found: " + missing);
            }

            List<Post> foreignPosts = foundPosts.stream()
                    .filter(post -> !(
                            (isUser && post.getAuthorUser() != null && post.getAuthorUser().getUuid().equals(clientUuid)) ||
                            (isCommunity && post.getAuthorCommunity() != null && post.getAuthorCommunity().getUuid().equals(authorCommunity.getUuid()))
                    ))
                    .toList();
            if(!foreignPosts.isEmpty()) {
                List<UUID> foreignUuids = foreignPosts.stream().map(Post::getUuid).toList();
                log.warn("Adding posts to album warn: posts don't belong to album author, album UUID={}, posts={}", albumUuid, foreignUuids);
                throw new ForbiddenException("Posts don't belong to album author: " + foreignUuids);
            }

            List<Post> albumPosts = album.getPosts();
            if(albumPosts == null) {
                albumPosts = new ArrayList<>();
                album.setPosts(albumPosts);
            }

            Set<Long> existingPostIds = albumPosts.stream().map(Post::getId).collect(Collectors.toSet());
            List<Post> postsToAdd = foundPosts.stream()
                    .filter(post -> !existingPostIds.contains(post.getId()))
                    .toList();

            albumPosts.addAll(postsToAdd);
            album.setWorksCount(album.getWorksCount() + postsToAdd.size());

            albumRepository.save(album);
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during adding posts to album, album UUID={}", albumUuid, ex);
            throw new ServiceException("Database error adding posts to album", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during adding posts to album, album UUID={}", albumUuid, ex);
            throw new ServiceException("Unexpected error adding posts to album", ex);
        }
    }

    /**
     * <h1>Удаление альбома по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Удаляет альбом из базы данных вместе с его изображением в хранилище
     *     через {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если текущий пользователь или альбом не найдены, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не автором альбома, выбрасывается {@link ForbiddenException}
     *         с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, ошибке удаления изображения из хранилища
     *         или любой другой ошибке, выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param uuid UUID альбома
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ResourceNotFoundException если текущий пользователь или альбом не найдены
     * @throws ForbiddenException если запрос отправлен не автором альбома
     * @throws ServiceException если произошла ошибка базы данных, удаления изображения или сервера
     */
    @Override
    @Transactional
    public void deleteAlbumByUuid(UUID uuid) {
        UUID clientUuid = authUtil.getPrincipalUuid();

        try {
            userRepository.findByUuid(clientUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Principal user not found"));
            Album album = albumRepository.findByUuid(uuid)
                    .orElseThrow(() -> new ResourceNotFoundException("Album not found"));

            boolean isUser = album.getAuthorUser() != null;
            boolean isCommunity = album.getAuthorCommunity() != null;

            if(isUser && !clientUuid.equals(album.getAuthorUser().getUuid())) {
                log.warn("Deleting album warn: client doesn't have access to delete album by this user, client UUID={}, album UUID={}", clientUuid, uuid);
                throw new ForbiddenException("You cannot delete album as this user");
            } else if(isCommunity) {
                boolean isOwnerOrAdmin = clientUuid.equals(album.getAuthorCommunity().getOwner().getUuid()) ||
                        album.getAuthorCommunity().getAdmins().stream().anyMatch(
                                admin -> clientUuid.equals(admin.getUuid())
                        );

                if(!isOwnerOrAdmin) {
                    log.warn("Deleting album warn: client doesn't have access to delete album by this community, client UUID={}, album UUID={}", clientUuid, uuid);
                    throw new ForbiddenException("You cannot delete album as this community");
                }
            }

            String imageUrl = album.getImageUrl();
            if(imageUrl != null && !imageUrl.isEmpty()) {
                imageUploaderService.deleteImage(imageUrl);
            }

            albumRepository.delete(album);
        } catch (ResourceNotFoundException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting album by UUID={}", uuid, ex);
            throw new ServiceException("Database error getting album", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting album by UUID={}", uuid, ex);
            throw new ServiceException("Unexpected error getting album", ex);
        }
    }
}
