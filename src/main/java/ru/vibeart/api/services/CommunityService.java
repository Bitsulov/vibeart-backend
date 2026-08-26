package ru.vibeart.api.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.community.CommunityCreateDetails;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.community.CommunityUpdateDetails;

import java.util.UUID;

/**
 * Сервис для работы с данными сообществ.
 */
public interface CommunityService {
    /**
     * Возвращает постраничный список сообществ за исключением сообществ, на которые подписан
     * пользователь по переданному UUID, и сообществ, которыми он владеет.
     *
     * @param userID UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации
     * @return страница с данными сообществ
     */
    Page<CommunityResponse> getCommunities(UUID userID, Pageable pageable);

    /**
     * Возвращает постраничный список сообществ, на которые подписан пользователь.
     *
     * @param userId UUID пользователя
     * @param pageable параметры пагинации
     * @return страница с данными сообществ
     */
    Page<CommunityResponse> getCommunitiesByUser(UUID userId, Pageable pageable);

    /**
     * Возвращает постраничный список сообществ, владельцем которых является текущий
     * аутентифицированный пользователь.
     *
     * @param pageable параметры пагинации
     * @return страница с данными сообществ
     */
    Page<CommunityResponse> getOwnedCommunities(Pageable pageable);

    /**
     * Ищет сообщества по названию и описанию, результаты отсортированы по релевантности запросу.
     * Если запрос начинается с {@code @}, поиск идёт по имени пользователя сообщества. Если передан
     * userId, из результата исключаются сообщества, на которые пользователь уже подписан, и
     * сообщества, которыми он владеет.
     *
     * @param query поисковый запрос
     * @param userId UUID пользователя, чьи подписки и собственные сообщества нужно исключить из результата
     * @param pageable параметры пагинации
     * @return страница с найденными сообществами
     */
    Page<CommunityResponse> getCommunitiesBySearch(String query, UUID userId, Pageable pageable);

    /**
     * Ищет по названию и описанию среди сообществ, на которые подписан пользователь, результаты
     * отсортированы по релевантности запросу. Если запрос начинается с {@code @}, поиск идёт по
     * имени пользователя сообщества.
     *
     * @param query поисковый запрос
     * @param userId UUID пользователя
     * @param pageable параметры пагинации
     * @return страница с найденными сообществами
     */
    Page<CommunityResponse> getCommunitiesByUserAndSearch(String query, UUID userId, Pageable pageable);

    /**
     * Возвращает сообщество по его UUID.
     *
     * @param id UUID сообщества
     * @return объект с данными сообщества
     */
    CommunityResponse getCommunityByUuid(UUID id);

    /**
     * Создаёт сообщество и возвращает его.
     *
     * @param communityCreateDetails объект с данными нового сообщества
     * @param file изображение сообщества
     * @return объект с данными созданного сообщества
     */
    CommunityResponse createCommunity(CommunityCreateDetails communityCreateDetails, MultipartFile file);

    /**
     * Изменяет сообщество и возвращает его.
     *
     * @param id UUID сообщества
     * @param communityUpdateDetails объект с новыми данными сообщества
     * @param file новое изображение сообщества
     * @return объект с данными изменённого сообщества
     */
    CommunityResponse updateCommunity(UUID id, CommunityUpdateDetails communityUpdateDetails, MultipartFile file);

    /**
     * Находит сообщество по UUID и удаляет его.
     *
     * @param id UUID сообщества
     */
    void deleteCommunity(UUID id);

    /**
     * Переключает подписку текущего пользователя на сообщество.
     *
     * @param id UUID сообщества, на которое оформляется или отменяется подписка
     */
    void toggleSubscription(UUID id);
}
