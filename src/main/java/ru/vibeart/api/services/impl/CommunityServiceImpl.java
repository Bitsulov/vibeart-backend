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
import ru.vibeart.api.dtos.community.CommunityCreateDetails;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.community.CommunityUpdateDetails;
import ru.vibeart.api.dtos.user.UserResponse;
import ru.vibeart.api.exceptions.ConflictException;
import ru.vibeart.api.exceptions.ForbiddenException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.exceptions.UnauthorizedException;
import ru.vibeart.api.models.entities.Community;
import ru.vibeart.api.models.entities.CommunitySubscription;
import ru.vibeart.api.models.entities.Subscription;
import ru.vibeart.api.models.entities.Tag;
import ru.vibeart.api.models.entities.User;
import ru.vibeart.api.repositories.CommunityRepository;
import ru.vibeart.api.repositories.CommunitySubscriptionRepository;
import ru.vibeart.api.repositories.SubscriptionRepository;
import ru.vibeart.api.repositories.TagRepository;
import ru.vibeart.api.repositories.UserRepository;
import ru.vibeart.api.services.CommunityService;
import ru.vibeart.api.utils.AuthUtil;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Реализация {@link CommunityService}.
 * <p>
 * Использует {@link AuthUtil} для определения текущего пользователя,
 * {@link ModelMapper} для преобразования сущности {@link Community} в DTO
 * и {@link ImageUploaderService} для загрузки и удаления аватаров сообществ.
 * </p>
 */
@Service
public class CommunityServiceImpl implements CommunityService {
    private final CommunityRepository communityRepository;
    private final CommunitySubscriptionRepository communitySubscriptionRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;
    private final ImageUploaderService imageUploaderService;
    private final AuthUtil authUtil;
    private final ModelMapper modelMapper;

    private final static Logger log = LoggerFactory.getLogger(CommunityServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param communityRepository репозиторий сообществ
     * @param communitySubscriptionRepository репозиторий подписок пользователей на сообщества
     * @param subscriptionRepository репозиторий подписок пользователей друг на друга
     * @param userRepository репозиторий пользователей
     * @param tagRepository репозиторий тегов
     * @param imageUploaderService сервис загрузки и удаления изображений
     * @param authUtil утилита для получения данных текущего аутентифицированного пользователя
     * @param modelMapper конвертер для преобразования DTO и сущностей
     */
    public CommunityServiceImpl(
            CommunityRepository communityRepository,
            CommunitySubscriptionRepository communitySubscriptionRepository,
            SubscriptionRepository subscriptionRepository,
            UserRepository userRepository,
            TagRepository tagRepository,
            ImageUploaderService imageUploaderService,
            AuthUtil authUtil,
            ModelMapper modelMapper
    ) {
        this.communityRepository = communityRepository;
        this.communitySubscriptionRepository = communitySubscriptionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.userRepository = userRepository;
        this.tagRepository = tagRepository;
        this.imageUploaderService = imageUploaderService;
        this.authUtil = authUtil;
        this.modelMapper = modelMapper;
    }

    /**
     * <h1>Получение списка сообществ</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список сообществ за исключением сообществ, на которые
     *     подписан пользователь с переданным UUID, и сообществ, которыми он владеет. Если
     *     UUID не передан, возвращаются все сообщества без исключений.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь с переданным UUID не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации
     * @return страница с данными сообществ
     * @throws ResourceNotFoundException если пользователь с переданным UUID не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityResponse> getCommunities(UUID userId, Pageable pageable) {
        try {
            if(userId != null) {
                userRepository.findByUuid(userId)
                        .orElseThrow(() -> {
                            log.warn("Getting communities warn: user not found with UUID={}", userId);
                            return new ResourceNotFoundException("User not found");
                        });
            }

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            Page<Community> communities = communityRepository.findAllExcludingUserSubscribesAndOwned(userId, pageable);

            return communities.map(community -> {
                CommunityResponse communityResponse = modelMapper.map(community, CommunityResponse.class);

                UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
                ownerResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                        .map(Subscription::isActive).orElse(false)
                );
                communityResponse.setOwner(ownerResponse);

                communityResponse.setAdmins(
                        community.getAdmins().stream()
                                .map(admin -> {
                                    UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                    adminResponse.setSubscribed(
                                            currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                    null :
                                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                            .map(Subscription::isActive).orElse(false)
                                    );
                                    return adminResponse;
                                })
                                .toList()
                );
                communityResponse.setTags(community.getTags().stream().map(Tag::getTitle).toList());

                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
                return communityResponse;
            });
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting communities", ex);
            throw new ServiceException("Database error getting communities", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting communities", ex);
            throw new ServiceException("Unexpected error getting communities", ex);
        }
    }

    /**
     * <h1>Полнотекстовый поиск сообществ</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Ищет сообщества по названию и описанию, результаты отсортированы по релевантности
     *     запросу. Если запрос начинается с {@code @}, поиск идёт по имени пользователя
     *     сообщества. Если передан UUID пользователя, из результата исключаются сообщества,
     *     на которые пользователь уже подписан, и сообщества, которыми он владеет.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь с переданным UUID не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param query поисковый запрос
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по релевантности
     * @throws ResourceNotFoundException если пользователь с переданным UUID не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityResponse> getCommunitiesBySearch(String query, UUID userId, Pageable pageable) {
        try {
            if(userId != null) {
                userRepository.findByUuid(userId)
                        .orElseThrow(() -> {
                            log.warn("Searching communities warn: user not found with UUID={}", userId);
                            return new ResourceNotFoundException("User not found");
                        });
            }

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            String trimmedQuery = query.trim();

            Page<Community> communities = trimmedQuery.startsWith("@") ?
                    communityRepository.searchByUsername(trimmedQuery.substring(1), userId, unsortedPageable) :
                    communityRepository.searchFullText(trimmedQuery, userId, unsortedPageable);

            return communities.map(community -> {
                CommunityResponse communityResponse = modelMapper.map(community, CommunityResponse.class);

                UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
                ownerResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                        .map(Subscription::isActive).orElse(false)
                );
                communityResponse.setOwner(ownerResponse);

                communityResponse.setAdmins(
                        community.getAdmins().stream()
                                .map(admin -> {
                                    UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                    adminResponse.setSubscribed(
                                            currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                    null :
                                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                            .map(Subscription::isActive).orElse(false)
                                    );
                                    return adminResponse;
                                })
                                .toList()
                );
                communityResponse.setTags(community.getTags().stream().map(Tag::getTitle).toList());

                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
                return communityResponse;
            });
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during searching communities", ex);
            throw new ServiceException("Database error searching communities", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during searching communities", ex);
            throw new ServiceException("Unexpected error searching communities", ex);
        }
    }

    /**
     * <h1>Полнотекстовый поиск по подпискам пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Ищет по названию и описанию среди сообществ, на которые подписан пользователь
     *     с переданным UUID, результаты отсортированы по релевантности запросу. Если запрос
     *     начинается с {@code @}, поиск идёт по имени пользователя сообщества.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь с переданным UUID не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param query поисковый запрос
     * @param userId UUID пользователя, среди подписок которого идёт поиск
     * @param pageable параметры пагинации (сортировка игнорируется)
     * @return страница с найденными сообществами, отсортированными по релевантности
     * @throws ResourceNotFoundException если пользователь с переданным UUID не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityResponse> getCommunitiesByUserAndSearch(String query, UUID userId, Pageable pageable) {
        try {
            userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.warn("Searching communities by user warn: user not found with UUID={}", userId);
                        return new ResourceNotFoundException("User not found");
                    });

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            String trimmedQuery = query.trim();

            Page<Community> communities = trimmedQuery.startsWith("@") ?
                    communityRepository.searchSubscribedByUsername(trimmedQuery.substring(1), userId, unsortedPageable) :
                    communityRepository.searchSubscribedFullText(trimmedQuery, userId, unsortedPageable);

            return communities.map(community -> {
                CommunityResponse communityResponse = modelMapper.map(community, CommunityResponse.class);

                UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
                ownerResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                        .map(Subscription::isActive).orElse(false)
                );
                communityResponse.setOwner(ownerResponse);

                communityResponse.setAdmins(
                        community.getAdmins().stream()
                                .map(admin -> {
                                    UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                    adminResponse.setSubscribed(
                                            currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                    null :
                                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                            .map(Subscription::isActive).orElse(false)
                                    );
                                    return adminResponse;
                                })
                                .toList()
                );
                communityResponse.setTags(community.getTags().stream().map(Tag::getTitle).toList());

                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
                return communityResponse;
            });
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during searching communities by user", ex);
            throw new ServiceException("Database error searching communities by user", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during searching communities by user", ex);
            throw new ServiceException("Unexpected error searching communities by user", ex);
        }
    }

    /**
     * <h1>Получение списка сообществ, на которые подписан пользователь</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список сообществ, на которые подписан пользователь
     *     с переданным UUID.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь с переданным UUID не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param userId UUID пользователя
     * @param pageable параметры пагинации
     * @return страница с данными сообществ, на которые подписан пользователь
     * @throws ResourceNotFoundException если пользователь с переданным UUID не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityResponse> getCommunitiesByUser(UUID userId, Pageable pageable) {
        try {
            userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.warn("Getting communities by user warn: user not found with UUID={}", userId);
                        return new ResourceNotFoundException("User not found");
                    });

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            Page<Community> communities = communityRepository.findAllUserSubscribed(userId, pageable);

            return communities.map(community -> {
                CommunityResponse communityResponse = modelMapper.map(community, CommunityResponse.class);

                UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
                ownerResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                        .map(Subscription::isActive).orElse(false)
                );
                communityResponse.setOwner(ownerResponse);

                communityResponse.setAdmins(
                        community.getAdmins().stream()
                                .map(admin -> {
                                    UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                    adminResponse.setSubscribed(
                                            currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                    null :
                                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                            .map(Subscription::isActive).orElse(false)
                                    );
                                    return adminResponse;
                                })
                                .toList()
                );
                communityResponse.setTags(community.getTags().stream().map(Tag::getTitle).toList());

                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
                return communityResponse;
            });
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting communities by user", ex);
            throw new ServiceException("Database error getting communities by user", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting communities by user", ex);
            throw new ServiceException("Unexpected error getting communities by user", ex);
        }
    }

    /**
     * <h1>Получение списка сообществ текущего пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список сообществ, владельцем которых является
     *     текущий аутентифицированный пользователь.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param pageable параметры пагинации
     * @return страница с данными собственных сообществ пользователя
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<CommunityResponse> getOwnedCommunities(Pageable pageable) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User currentUser = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Getting owned communities error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });

            Page<Community> communities = communityRepository.findAllByOwner_Uuid(userId, pageable);

            return communities.map(community -> {
                CommunityResponse communityResponse = modelMapper.map(community, CommunityResponse.class);

                UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
                ownerResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                        .map(Subscription::isActive).orElse(false)
                );
                communityResponse.setOwner(ownerResponse);

                communityResponse.setAdmins(
                        community.getAdmins().stream()
                                .map(admin -> {
                                    UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                    adminResponse.setSubscribed(
                                            currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                    null :
                                                    subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                            .map(Subscription::isActive).orElse(false)
                                    );
                                    return adminResponse;
                                })
                                .toList()
                );
                communityResponse.setTags(community.getTags().stream().map(Tag::getTitle).toList());

                communityResponse.setSubscribed(
                        currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                                null :
                                communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                        .map(CommunitySubscription::isActive).orElse(false)
                );
                return communityResponse;
            });
        } catch (ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting owned communities, UUID={}", userId, ex);
            throw new ServiceException("Database error getting owned communities", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting owned communities, UUID={}", userId, ex);
            throw new ServiceException("Unexpected error getting owned communities", ex);
        }
    }

    /**
     * <h1>Получение сообщества по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает данные сообщества по его UUID из базы данных.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если сообщество не найдено, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID сообщества
     * @return объект с данными сообщества
     * @throws ResourceNotFoundException если сообщество не найдено
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public CommunityResponse getCommunityByUuid(UUID id) {
        try {
            Community community = communityRepository.findByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Getting community warn: community not found, UUID={}", id);
                        return new ResourceNotFoundException("Community not found");
                    });

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            CommunityResponse response = modelMapper.map(community, CommunityResponse.class);

            UserResponse ownerResponse = modelMapper.map(community.getOwner(), UserResponse.class);
            ownerResponse.setSubscribed(
                    currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                            null :
                            subscriptionRepository.findByFollowerAndFollowing(currentUser, community.getOwner())
                                    .map(Subscription::isActive).orElse(false)
            );
            response.setOwner(ownerResponse);

            response.setAdmins(
                    community.getAdmins().stream()
                            .map(admin -> {
                                UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                                adminResponse.setSubscribed(
                                        currentUser == null || currentUser.getUuid().equals(admin.getUuid()) ?
                                                null :
                                                subscriptionRepository.findByFollowerAndFollowing(currentUser, admin)
                                                        .map(Subscription::isActive).orElse(false)
                                );
                                return adminResponse;
                            })
                            .toList()
            );
            response.setTags(community.getTags().stream().map(Tag::getTitle).toList());

            response.setSubscribed(
                    currentUser == null || currentUser.getUuid().equals(community.getOwner().getUuid()) ?
                            null :
                            communitySubscriptionRepository.findByFollowerAndFollowing(currentUser, community)
                                    .map(CommunitySubscription::isActive).orElse(false)
            );
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting community", ex);
            throw new ServiceException("Database error getting communities by user", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting community", ex);
            throw new ServiceException("Unexpected error getting communities by user", ex);
        }
    }

    /**
     * <h1>Создание сообщества</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Создаёт сообщество от имени текущего аутентифицированного пользователя и
     *     возвращает его. Владелец сообщества не может быть указан в списке
     *     администраторов. Для загрузки аватара используется {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если владелец указан в списке администраторов, выбрасывается
     *         {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если пользователь пытается создать сообщество от имени другого пользователя,
     *         выбрасывается {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         Если один из тегов или администраторов не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если переданное имя пользователя сообщества уже занято, выбрасывается
     *         {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, загрузки изображения или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param communityCreateDetails объект с данными нового сообщества
     * @param file изображение сообщества
     * @return объект с данными созданного сообщества
     * @throws IllegalArgumentException если владелец указан в списке администраторов
     * @throws ForbiddenException если пользователь пытается создать сообщество от имени другого пользователя
     * @throws ResourceNotFoundException если один из тегов или администраторов не найден
     * @throws ConflictException если переданное имя пользователя сообщества уже занято
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public CommunityResponse createCommunity(CommunityCreateDetails communityCreateDetails, MultipartFile file) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Create community error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });
            if(!userId.equals(communityCreateDetails.getUserId())) {
                log.warn("Create community warn: client has not access to create community by user with UUID={}", communityCreateDetails.getUserId());
                throw new ForbiddenException("You cannot create community by this user");
            }

            String username = communityCreateDetails.getUsername();
            if(username != null && communityRepository.existsByUsername(username)) {
                log.warn("Create community warn: username is already taken, username={}", username);
                throw new ConflictException("Username is already taken");
            }

            Community community = new Community();
            community.setUuid(UUID.randomUUID());
            community.setName(communityCreateDetails.getTitle());
            community.setUsername(communityCreateDetails.getUsername());
            community.setDescription(communityCreateDetails.getDescription());

            List<Tag> tags = new ArrayList<>(communityCreateDetails.getTagsTitles().stream()
                    .map(tagName ->
                            tagRepository.findByTitle(tagName)
                                .orElseThrow(() -> {
                                    log.warn("Create community warn: tag not found, tag={}", tagName);
                                    return new ResourceNotFoundException("Tag " + tagName + " not found");
                                })).toList());
            community.setTags(tags);

            List<User> admins = new ArrayList<>(communityCreateDetails.getAdminsUuids().stream()
                    .map(adminUuid -> {
                            if(!adminUuid.equals(user.getUuid())) {
                                return userRepository.findByUuid(adminUuid)
                                        .orElseThrow(() -> {
                                            log.warn("Create community warn: admin user not found, UUID={}", adminUuid);
                                            return new ResourceNotFoundException("User with UUID " + adminUuid + " not found");
                                        });
                            } else {
                                log.warn("Create community warn: admin user is equal to owner, UUID={}", adminUuid);
                                throw new IllegalArgumentException("Owner user cannot be admin at the same time");
                            }
                    }).toList());
            community.setOwner(user);
            community.setAdmins(admins);
            community.setCreatedAt(Instant.now());
            community.setEnabled(true);

            if(file != null && !file.isEmpty()) {
                String avatar = imageUploaderService.uploadImage(file);
                community.setAvatarUrl(avatar);
            }

            communityRepository.save(community);
            log.info("END creating community: UUID={}", community.getUuid());

            CommunityResponse response = modelMapper.map(community, CommunityResponse.class);
            response.setOwner(modelMapper.map(community.getOwner(), UserResponse.class));
            response.setAdmins(community.getAdmins().stream()
                    .map(admin -> {
                        UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                        adminResponse.setSubscribed(
                                subscriptionRepository.findByFollowerAndFollowing(user, admin)
                                        .map(Subscription::isActive).orElse(false)
                        );
                        return adminResponse;
                    })
                    .toList()
            );
            response.setTags(community.getTags().stream().map(Tag::getTitle).toList());
            return response;
        } catch (ResourceNotFoundException | ForbiddenException | ServiceException | IllegalArgumentException |
                 ConflictException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during creating community", ex);
            throw new ServiceException("Database error creating community", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating community", ex);
            throw new ServiceException("Unexpected error creating community", ex);
        }
    }

    /**
     * <h1>Изменение сообщества</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит сообщество по UUID, меняет его данные и возвращает его. Владелец сообщества
     *     не может быть указан в списке администраторов. Если {@code isDeleteAvatar} — true,
     *     то аватар удаляется из объектного хранилища и базы данных. Для загрузки и удаления
     *     изображений используется {@link ImageUploaderService}.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если владелец указан в списке администраторов, выбрасывается
     *         {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не владельцем сообщества, выбрасывается
     *         {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         Если сообщество, один из тегов или администраторов не найден, выбрасывается
     *         {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если переданное имя пользователя сообщества уже занято, выбрасывается
     *         {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, загрузки изображения или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID сообщества
     * @param communityUpdateDetails объект с новыми данными сообщества
     * @param file новое изображение сообщества
     * @return объект с данными изменённого сообщества
     * @throws IllegalArgumentException если владелец указан в списке администраторов
     * @throws ForbiddenException если запрос отправлен не владельцем сообщества
     * @throws ResourceNotFoundException если сообщество, один из тегов или администраторов не найден
     * @throws ConflictException если переданное имя пользователя сообщества уже занято
     * @throws ServiceException если произошла ошибка базы данных, загрузки изображения или сервера
     */
    @Override
    @Transactional
    public CommunityResponse updateCommunity(UUID id, CommunityUpdateDetails communityUpdateDetails, MultipartFile file) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Update community error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });
            Community community = communityRepository.findByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Update community warn: community not found with UUID={}", id);
                        return new ResourceNotFoundException("Community not found");
                    });
            if(!user.getUuid().equals(community.getOwner().getUuid())) {
                log.warn("Update community warn: client has not access to update community by user with UUID={}", userId);
                throw new ForbiddenException("You cannot update community by this user");
            }

            String newUsername = communityUpdateDetails.getUsername();
            if(newUsername != null
                    && !newUsername.equals(community.getUsername())
                    && communityRepository.existsByUsername(newUsername)) {
                log.warn("Update community warn: username is already taken, username={}", newUsername);
                throw new ConflictException("Username is already taken");
            }

            community.setName(communityUpdateDetails.getTitle());
            community.setUsername(communityUpdateDetails.getUsername());
            community.setDescription(communityUpdateDetails.getDescription());

            List<Tag> tags = new ArrayList<>(communityUpdateDetails.getTagsTitles().stream()
                    .map(tagName ->
                            tagRepository.findByTitle(tagName)
                                    .orElseThrow(() -> {
                                        log.warn("Update community warn: tag not found, tag={}", tagName);
                                        return new ResourceNotFoundException("Tag " + tagName + " not found");
                                    })).toList());
            community.setTags(tags);

            List<User> admins = new ArrayList<>(communityUpdateDetails.getAdminsUuids().stream()
                    .map(adminUuid -> {
                        if(!adminUuid.equals(community.getOwner().getUuid())) {
                            return userRepository.findByUuid(adminUuid)
                                    .orElseThrow(() -> {
                                        log.warn("Update community warn: admin user not found, UUID={}", adminUuid);
                                        return new ResourceNotFoundException("User with UUID " + adminUuid + " not found");
                                    });
                        } else {
                            log.warn("Update community warn: admin user is equal to owner, UUID={}", adminUuid);
                            throw new IllegalArgumentException("Owner user cannot be admin at the same time");
                        }
                    }).toList());
            community.setAdmins(admins);

            final boolean isEmptyAvatar = community.getAvatarUrl() == null || community.getAvatarUrl().isEmpty();
            String imageUrl = null;

            if(communityUpdateDetails.isDeleteAvatar()) {
                if(!isEmptyAvatar) {
                    imageUploaderService.deleteImage(community.getAvatarUrl());
                }
                community.setAvatarUrl(null);
            } else if(file != null && !file.isEmpty()) {
                imageUrl = imageUploaderService.uploadImage(file);
                if(!isEmptyAvatar) {
                    imageUploaderService.deleteImage(community.getAvatarUrl());
                }
                community.setAvatarUrl(imageUrl);
            }

            communityRepository.save(community);
            log.info("END updating community info: UUID={}, info={}, avatar={}", community.getUuid(), communityUpdateDetails, imageUrl);

            CommunityResponse response = modelMapper.map(community, CommunityResponse.class);
            response.setOwner(modelMapper.map(community.getOwner(), UserResponse.class));
            response.setAdmins(community.getAdmins().stream()
                    .map(admin -> {
                        UserResponse adminResponse = modelMapper.map(admin, UserResponse.class);
                        adminResponse.setSubscribed(
                                subscriptionRepository.findByFollowerAndFollowing(user, admin)
                                        .map(Subscription::isActive).orElse(false)
                        );
                        return adminResponse;
                    })
                    .toList()
            );
            response.setTags(community.getTags().stream().map(Tag::getTitle).toList());
            return response;
        } catch (ResourceNotFoundException | ForbiddenException | ServiceException | IllegalArgumentException |
                 ConflictException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during updating community", ex);
            throw new ServiceException("Database error updating community", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating community", ex);
            throw new ServiceException("Unexpected error updating community", ex);
        }
    }

    /**
     * <h1>Удаление сообщества</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит сообщество по UUID и удаляет его вместе с аватаром в объектном
     *     хранилище, если он был установлен.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен не владельцем сообщества, выбрасывается
     *         {@link ForbiddenException} с кодом ответа <b>403</b>
     *     </li>
     *     <li>
     *         Если сообщество не найдено, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, удаления изображения или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID сообщества
     * @throws ForbiddenException если запрос отправлен не владельцем сообщества
     * @throws ResourceNotFoundException если сообщество не найдено
     * @throws ServiceException если произошла ошибка базы данных, удаления изображения или сервера
     */
    @Override
    @Transactional
    public void deleteCommunity(UUID id) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Delete community error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });
            Community community = communityRepository.findByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Delete community warn: community not found with UUID={}", id);
                        return new ResourceNotFoundException("Community not found");
                    });
            if(!user.getUuid().equals(community.getOwner().getUuid())) {
                log.warn("Delete community warn: client has not access to delete community by user with UUID={}", userId);
                throw new ForbiddenException("You cannot delete community by this user");
            }

            String avatarUrl = community.getAvatarUrl();
            if(avatarUrl != null && !avatarUrl.isEmpty()) {
                imageUploaderService.deleteImage(avatarUrl);
            }

            communityRepository.delete(community);
        } catch (ResourceNotFoundException | ServiceException | ForbiddenException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting community, UUID={}", id, ex);
            throw new ServiceException("Database error deleting community", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting community, UUID={}", id, ex);
            throw new ServiceException("Unexpected error deleting community", ex);
        }
    }

    /**
     * <h1>Переключение подписки на сообщество</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Оформляет или отменяет подписку текущего аутентифицированного пользователя
     *     на сообщество, переданное в UUID.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь пытается подписаться на собственное сообщество, выбрасывается
     *         {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если сообщество, на которое оформляется подписка, не найдено,
     *         выбрасывается {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID сообщества, на которое оформляется или отменяется подписка
     * @throws IllegalArgumentException если пользователь пытается подписаться на собственное сообщество
     * @throws ResourceNotFoundException если сообщество, на которое оформляется подписка, не найдено
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void toggleSubscription(UUID id) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Toggle subscription error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });
            Community community = communityRepository.findWithLockByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Toggle subscription warn: community not found with UUID={}", id);
                        return new ResourceNotFoundException("Community not found");
                    });
            if(community.getOwner().getUuid().equals(userId)) {
                log.warn("Toggle subscription warn: user tries to subscribe to own community, UUID={}", userId);
                throw new IllegalArgumentException("You cannot subscribe to your own community");
            }

            CommunitySubscription subscription = communitySubscriptionRepository
                    .findByFollowerAndFollowing(user, community)
                    .orElseGet(() -> {
                        CommunitySubscription newSubscription = new CommunitySubscription();
                        newSubscription.setFollower(user);
                        newSubscription.setFollowing(community);
                        newSubscription.setActive(false);
                        return newSubscription;
                    });

            if(!subscription.isActive()) {
                subscription.setActive(true);
                communityRepository.incrementSubscribersCount(community.getId());
                userRepository.incrementSubscribesCount(user.getId());
            } else {
                subscription.setActive(false);
                communityRepository.decrementSubscribersCount(community.getId());
                userRepository.decrementSubscribesCount(user.getId());
            }

            communitySubscriptionRepository.save(subscription);
        } catch (ResourceNotFoundException | ServiceException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during toggling subscription, community UUID={}, user UUID={}", id, userId, ex);
            throw new ServiceException("Database error toggling subscription", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during toggling subscription, community UUID={}, user UUID={}", id, userId, ex);
            throw new ServiceException("Unexpected error toggling subscription", ex);
        }
    }
}
