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
import ru.vibeart.api.dtos.tag.TagCreateRequest;
import ru.vibeart.api.dtos.tag.TagResponse;
import ru.vibeart.api.dtos.tag.TagUpdateRequest;
import ru.vibeart.api.exceptions.ConflictException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.models.entities.Tag;
import ru.vibeart.api.repositories.TagRepository;
import ru.vibeart.api.services.TagService;

import java.time.Instant;
import java.util.Optional;

/**
 * Реализация {@link TagService}.
 * <p>
 * Использует {@link ModelMapper} для преобразования сущности {@link Tag} в DTO и обратно.
 * </p>
 */
@Service
public class TagServiceImpl implements TagService {
    private final TagRepository tagRepository;
    private final ModelMapper modelMapper;

    private final static Logger log = LoggerFactory.getLogger(TagServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param tagRepository репозиторий тегов
     * @param modelMapper конвертер для преобразования DTO и сущностей
     */
    public TagServiceImpl(TagRepository tagRepository, ModelMapper modelMapper) {
        this.tagRepository = tagRepository;
        this.modelMapper = modelMapper;
    }

    /**
     * <h1>Получение списка тегов</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает постраничный список активных тегов.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param pageable параметры пагинации
     * @return страница с данными тегов
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> getTags(Pageable pageable) {
        try {
            return tagRepository.findAllByEnabledTrue(pageable).map(tag -> modelMapper.map(tag, TagResponse.class));
        } catch (DataAccessException ex) {
            log.error("Database error during getting tags", ex);
            throw new ServiceException("Database error getting tags", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting tags", ex);
            throw new ServiceException("Unexpected error getting tags", ex);
        }
    }

    /**
     * <h1>Поиск тегов</h1>
     *
     * <h2>Назначение</h2>
     * <p>Ищет активные теги, название которых содержит переданную подстроку, без учёта регистра.</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param query поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с найденными тегами
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<TagResponse> searchTags(String query, Pageable pageable) {
        try {
            return tagRepository.findByEnabledTrueAndTitleContainingIgnoreCase(query.trim(), pageable)
                    .map(tag -> modelMapper.map(tag, TagResponse.class));
        } catch (DataAccessException ex) {
            log.error("Database error during searching tags", ex);
            throw new ServiceException("Database error searching tags", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during searching tags", ex);
            throw new ServiceException("Unexpected error searching tags", ex);
        }
    }

    /**
     * <h1>Создание тега</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Создаёт или активирует тег и возвращает его.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если переданное название тега уже занято активным тегом, выбрасывается
     *         {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param tagCreateDetails объект с данными нового тега
     * @return объект с данными созданного тега
     * @throws ConflictException если переданное название тега уже занято активным тегом
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public TagResponse createTag(TagCreateRequest tagCreateDetails) {
        try {
            String title = tagCreateDetails.getTitle();
            Optional<Tag> existingTag = tagRepository.findByTitle(title);

            if(existingTag.isPresent()) {
                Tag disabledTag = existingTag.get();
                if(disabledTag.isEnabled()) {
                    log.warn("Create tag warn: title is already taken, title={}", title);
                    throw new ConflictException("Tag title is already taken");
                }

                disabledTag.setEnabled(true);
                tagRepository.save(disabledTag);
                log.info("END creating tag: re-enabled previously deleted tag, title={}", title);

                return modelMapper.map(disabledTag, TagResponse.class);
            }

            Tag tag = modelMapper.map(tagCreateDetails, Tag.class);
            tag.setCreatedAt(Instant.now());
            tag.setEnabled(true);

            tagRepository.save(tag);
            log.info("END creating tag: title={}", tag.getTitle());

            return modelMapper.map(tag, TagResponse.class);
        } catch (ConflictException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during creating tag", ex);
            throw new ServiceException("Database error creating tag", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during creating tag", ex);
            throw new ServiceException("Unexpected error creating tag", ex);
        }
    }

    /**
     * <h1>Изменение тега</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит активный тег по текущему названию, изменяет его название и возвращает.
     *     Если новое название принадлежит отключённому тегу, вместо
     *     переименования этот тег включается, а изменяемый тег отключается.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если активный тег не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если переданное название тега уже занято активным тегом, выбрасывается
     *         {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param title текущее название тега
     * @param tagUpdateDetails объект с новыми данными тега
     * @return объект с данными тега
     * @throws ResourceNotFoundException если активный тег не найден
     * @throws ConflictException если переданное название тега уже занято активным тегом
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public TagResponse updateTag(String title, TagUpdateRequest tagUpdateDetails) {
        try {
            Tag tag = tagRepository.findByTitleAndEnabledTrue(title)
                    .orElseThrow(() -> {
                        log.warn("Update tag warn: tag not found, title={}", title);
                        return new ResourceNotFoundException("Tag not found");
                    });

            String newTitle = tagUpdateDetails.getTitle();
            if(!newTitle.equals(tag.getTitle())) {
                Optional<Tag> existingTag = tagRepository.findByTitle(newTitle);
                if(existingTag.isPresent()) {
                    Tag other = existingTag.get();
                    if(other.isEnabled()) {
                        log.warn("Update tag warn: title is already taken, title={}", newTitle);
                        throw new ConflictException("Tag title is already taken");
                    }

                    other.setEnabled(true);
                    tagRepository.save(other);

                    tag.setEnabled(false);
                    tagRepository.save(tag);
                    log.info("END updating tag: title \"{}\" was disabled, re-enabled instead of renaming \"{}\"",
                            newTitle, tag.getTitle());

                    return modelMapper.map(other, TagResponse.class);
                }
            }

            tag.setTitle(newTitle);
            tagRepository.save(tag);
            log.info("END updating tag: title={}", tag.getTitle());

            return modelMapper.map(tag, TagResponse.class);
        } catch (ResourceNotFoundException | ConflictException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during updating tag", ex);
            throw new ServiceException("Database error updating tag", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating tag", ex);
            throw new ServiceException("Unexpected error updating tag", ex);
        }
    }

    /**
     * <h1>Удаление тега</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит активный тег по названию и отключает его (мягкое удаление, флаг
     *     {@code enabled}). Уже существующие ссылки на тег у публикаций и сообществ
     *     не затрагиваются — тег просто перестаёт быть доступен через список, поиск,
     *     изменение и повторное удаление.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если активный тег не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param title название тега
     * @throws ResourceNotFoundException если активный тег не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void deleteTag(String title) {
        try {
            Tag tag = tagRepository.findByTitleAndEnabledTrue(title)
                    .orElseThrow(() -> {
                        log.warn("Delete tag warn: tag not found, title={}", title);
                        return new ResourceNotFoundException("Tag not found");
                    });

            tag.setEnabled(false);
            tagRepository.save(tag);
        } catch (ResourceNotFoundException | ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting tag, title={}", title, ex);
            throw new ServiceException("Database error deleting tag", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting tag, title={}", title, ex);
            throw new ServiceException("Unexpected error deleting tag", ex);
        }
    }
}
