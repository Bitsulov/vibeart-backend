package ru.vibeart.api.services.impl;

import org.hibernate.service.spi.ServiceException;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import ru.vibeart.api.dtos.user.*;
import ru.vibeart.api.exceptions.ConflictException;
import ru.vibeart.api.exceptions.GoneException;
import ru.vibeart.api.exceptions.ResourceNotFoundException;
import ru.vibeart.api.exceptions.UnauthorizedException;
import ru.vibeart.api.models.entities.Community;
import ru.vibeart.api.models.entities.Subscription;
import ru.vibeart.api.models.entities.User;
import ru.vibeart.api.models.entities.VerificationCode;
import ru.vibeart.api.models.enums.VerificationCodesType;
import ru.vibeart.api.repositories.SubscriptionRepository;
import ru.vibeart.api.repositories.UserRepository;
import ru.vibeart.api.repositories.VerificationCodeRepository;
import ru.vibeart.api.services.UserService;
import ru.vibeart.api.utils.AuthUtil;

import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Реализация {@link UserService}.
 * <p>
 * Использует {@link AuthUtil} для определения текущего пользователя,
 * {@link ModelMapper} для преобразования сущности {@link User} в DTO
 * и {@link PasswordEncoder} для хеширования паролей.
 * </p>
 */
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final VerificationCodeRepository verificationCodeRepository;
    private final ModelMapper modelMapper;
    private final AuthUtil authUtil;
    private final ImageUploaderService imageUploaderService;
    private final EmailMessageProducer emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.code-expiration-time}")
    private Duration codeExpirationTime;

    @Value("${app.allow-new-code-time}")
    private Duration allowNewCodeTime;

    private static final Logger log = LoggerFactory.getLogger(UserServiceImpl.class);

    /**
     * Конструктор с внедрением зависимостей.
     *
     * @param userRepository репозиторий пользователей
     * @param subscriptionRepository репозиторий подписок пользователей друг на друга
     * @param verificationCodeRepository репозиторий кодов подтверждения
     * @param modelMapper конвертер для преобразования DTO и сущностей
     * @param authUtil утилита для получения данных текущего аутентифицированного пользователя
     * @param imageUploaderService сервис обработки изображений
     * @param emailService сервис отправки писем по электронной почте
     * @param passwordEncoder кодировщик паролей пользователя
     */
    public UserServiceImpl(
            UserRepository userRepository,
            SubscriptionRepository subscriptionRepository,
            VerificationCodeRepository verificationCodeRepository,
            ModelMapper modelMapper,
            AuthUtil authUtil,
            ImageUploaderService imageUploaderService,
            EmailMessageProducer emailService,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.verificationCodeRepository = verificationCodeRepository;
        this.modelMapper = modelMapper;
        this.authUtil = authUtil;
        this.imageUploaderService = imageUploaderService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    private static final SecureRandom secureRandom = new SecureRandom();
    /**
     * Генерация шестизначного кода для кодов подтверждения
     */
    private String generateSixDigitCode() {
        int number = secureRandom.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    /**
     * <h1>Получение данных аутентифицированного пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает данные текущего аутентифицированного
     *     пользователя используя {@link AuthUtil}
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @return объект с данными пользователя, необходимыми для отображения в текущей аутентифицированной сессии
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws UnauthorizedException если пользователь не авторизован
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    public UserDetailResponse getPrincipalUser() {
        UUID ownerUuid = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(ownerUuid)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + ownerUuid));

            UserDetailResponse response = modelMapper.map(user, UserDetailResponse.class);
            response.setRole(user.getRole().getName().name());
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting principal user, UUID={}", ownerUuid, ex);
            throw new ServiceException("Database error during getting principal user", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting principal user, UUID={}", ownerUuid, ex);
            throw new ServiceException("Unexpected error during getting principal user", ex);
        }
    }

    /**
     * <h1>Получение данных пользователя по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>Возвращает данные пользователя по его UUID из базы данных</p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param id UUID пользователя
     * @return объект с данными пользователя, необходимыми для отображения в профиле
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    public UserResponse getUserByUUID(UUID id) {
        try {
            User user = userRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + id));

            User currentUser = authUtil.getIsAuthenticated() ?
                    userRepository.findByUuid(authUtil.getPrincipalUuid()).orElse(null) : null;

            UserResponse response = modelMapper.map(user, UserResponse.class);
            response.setSubscribed(
                    currentUser == null || currentUser.getUuid().equals(user.getUuid()) ?
                            null :
                            subscriptionRepository.findByFollowerAndFollowing(currentUser, user)
                                    .map(Subscription::isActive).orElse(false)
            );
            return response;
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting user by UUID", ex);
            throw new ServiceException("Database error during getting user", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting user by UUID", ex);
            throw new ServiceException("Unexpected error during getting user", ex);
        }
    }

    /**
     * <h1>Изменение данных пользователя по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит пользователя по UUID и меняет его данные. Если <code>isEmptyAvatar</code> - true,
     *     то аватар удаляется из объектного хранилища и базы данных. Для загрузки и удаления изображений
     *     используется {@link ImageUploaderService}
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь прислал флаг удаления аватара и непустой файл (конфликтующие действия),
     *         выбрасывается {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если переданное имя пользователя (username) отличается от текущего и уже занято другим
     *         пользователем, выбрасывается {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, сервиса загрузки файлов или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param id UUID пользователя
     * @param userUpdateDetails объект с новыми данными пользователя
     * @param file новый аватар пользователя
     * @return объект с данными пользователя, необходимыми для отображения в профиле
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws IllegalArgumentException если пользователь пытается удалить аватар, но в теле запроса есть непустой файл
     * @throws ConflictException если переданное имя пользователя уже занято другим пользователем
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public UserResponse updateUserByUUID(UUID id, UserUpdateDetails userUpdateDetails, MultipartFile file) {
        try {
            User user = userRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + id));

            if(userUpdateDetails.isDeleteAvatar() && (file != null && !file.isEmpty())) {
                log.warn("Updating user failed: isDeleteAvatar=true and non-empty file provided in the one request, UUID={}", id);
                throw new IllegalArgumentException("Cannot delete the avatar and upload a new one at the same time. Please choose only one action");
            }

            String newUsername = userUpdateDetails.getUsername();
            if(!newUsername.equals(user.getUsername()) && userRepository.existsByUsername(newUsername)) {
                log.warn("Updating user failed: username already taken, username={}, UUID={}", newUsername, id);
                throw new ConflictException("Username is already taken");
            }

            user.setName(userUpdateDetails.getName());
            user.setUsername(newUsername);
            user.setDescription(userUpdateDetails.getDescription());

            final boolean isEmptyAvatar = user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty();
            String imageUrl = null;

            if(userUpdateDetails.isDeleteAvatar()) {
                if(!isEmptyAvatar) {
                    imageUploaderService.deleteImage(user.getAvatarUrl());
                }
                user.setAvatarUrl(null);
            } else if(file != null && !file.isEmpty()) {
                if(!isEmptyAvatar) {
                    imageUploaderService.deleteImage(user.getAvatarUrl());
                }
                imageUrl = imageUploaderService.uploadImage(file);
                user.setAvatarUrl(imageUrl);
            }

            userRepository.save(user);

            log.info("END updating user info: UUID={}, info={}, avatar={}", user.getUuid(), userUpdateDetails, imageUrl);
            return modelMapper.map(user, UserResponse.class);
        } catch (ResourceNotFoundException | IllegalArgumentException | ConflictException ex) {
            throw ex;
        } catch (IOException ex) {
            log.error("Image load error during updating user for UUID={}", id, ex);
            throw new ServiceException("File loading error during updating", ex);
        } catch (DataAccessException ex) {
            log.error("Database error during updating user for UUID={}", id, ex);
            throw new ServiceException("Database error during updating", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating user for UUID={}", id, ex);
            throw new ServiceException("Unexpected error during updating", ex);
        }
    }

    /**
     * <h1>Изменение адреса электронной почты пользователя по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Проверяет существование пользователя по указанному адресу электронной почты.
     *     Если его не существует, то удаляет старый код подтверждения при наличии и создаёт новый.
     *     После создания на переданный адрес электронной почты отправляется код подтверждения,
     *     используя {@link EmailMessageProducer}
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если пользователь с указанным адресом электронной почты уже существует,
     *         выбрасывается {@link ConflictException} с кодом ответа <b>409</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен раньше двух минут с момента отправки предыдущего кода,
     *         выбрасывается исключение {@link IllegalArgumentException} с кодом ошибки <b>400</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, сервиса загрузки файлов или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param id UUID пользователя
     * @param changeEmailRequest объект с адресом электронной почты пользователя
     * @param locale объект locale с текущим языком пользователя
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws ConflictException если пользователь с таким адресом электронной почты уже существует
     * @throws IllegalAccessError если запрос отправлен слишком рано
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void changeEmail(UUID id, ChangeEmailRequest changeEmailRequest, Locale locale) {
        try {
            final String email = changeEmailRequest.getEmail();

            User user = userRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + id));

            if(userRepository.existsByEmail(email)) {
                log.warn("Change email error: user already exists, email={}, requesting user UUID={}", email, id);
                throw new ConflictException("User already exists");
            }

            Optional<VerificationCode> oldCode = verificationCodeRepository
                    .findByUserAndType(user, VerificationCodesType.CHANGE_EMAIL);

            oldCode.ifPresent(i -> {
                Instant sentTime = i.getCodeExpiresAt().minus(codeExpirationTime);
                if(Instant.now().isBefore(sentTime.plus(allowNewCodeTime))) {
                    log.warn("Change email rejected: verification code was requested too recently, UUID={}", id);
                    throw new IllegalArgumentException("Please wait before requesting a new verification code");
                }

                verificationCodeRepository.delete(i);
                verificationCodeRepository.flush();
                log.debug("Deleted change email old verification code during change email request for UUID={}: code={}, expiresAt={}, email={}",
                        user.getUuid(), i.getCode(), i.getCodeExpiresAt(), i.getTargetValue());
            });

            String code = generateSixDigitCode();
            VerificationCode verificationCode = new VerificationCode(
                    code,
                    Instant.now().plus(codeExpirationTime),
                    changeEmailRequest.getEmail(),
                    user,
                    VerificationCodesType.CHANGE_EMAIL);
            log.debug("Generated change email verification code for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), code, verificationCode.getCodeExpiresAt(), verificationCode.getTargetValue());

            verificationCodeRepository.save(verificationCode);
            emailService.sendChangeEmailVerificationEmail(email, code, locale.getLanguage());
            log.info("END change email request: verification code sent to email={}", changeEmailRequest.getEmail());
        } catch (ResourceNotFoundException | ConflictException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during updating email for UUID={}", id, ex);
            throw new ServiceException("Database error during updating", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating email for UUID={}", id, ex);
            throw new ServiceException("Unexpected error during updating", ex);
        }
    }

    /**
     * <h1>Повторная отправка кода подтверждения смены почты</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *    Находит пользователя по адресу электронной почты, удаляет его старый код подтверждения
     *    и генерирует новый. Созданный код отправляется в отдельный микросервис
     *    с помощью {@link EmailMessageProducer}
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь или старый код подтверждения не найден, выбрасывается исключение
     *         {@link ResourceNotFoundException} с кодом ошибки <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен раньше двух минут с момента отправки предыдущего кода,
     *         выбрасывается исключение {@link IllegalArgumentException} с кодом ошибки <b>400</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается исключение
     *         {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param sendCodeEmailRequest объект с адресом почты для повторной отправки кода
     * @param locale объект locale с текущим языком пользователя
     * @throws ResourceNotFoundException если пользователь или прошлый код подтверждения не найден
     * @throws IllegalArgumentException если запрос отправлен слишком рано
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void sendChangeEmail(SendCodeEmailRequest sendCodeEmailRequest, Locale locale) {
        String email = sendCodeEmailRequest.getEmail();

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.warn("Sending email verification code again error: user not found, email={}", email);
                        return new ResourceNotFoundException("User not found");
                    });

            VerificationCode verificationCode = verificationCodeRepository.findByUserAndType(user, VerificationCodesType.CHANGE_EMAIL)
                    .orElseThrow(() -> {
                        log.warn("Sending email verification code again error: previous code not found, email={}", email);
                        return new ResourceNotFoundException("Previous code not found");
                    });

            Instant sentTime = verificationCode.getCodeExpiresAt().minus(codeExpirationTime);
            if(Instant.now().isBefore(sentTime.plus(allowNewCodeTime))) {
                log.warn("Sending email verification code again rejected: too soon, email={}", email);
                throw new IllegalArgumentException("Please wait before requesting a new verification code");
            }

            String newEmail = verificationCode.getTargetValue();

            verificationCodeRepository.delete(verificationCode);
            verificationCodeRepository.flush();
            log.debug("Deleted change email old verification code during sending code again for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), verificationCode.getCode(), verificationCode.getCodeExpiresAt(), verificationCode.getTargetValue());

            String code = generateSixDigitCode();
            VerificationCode newVerificationCode = new VerificationCode(
                    code,
                    Instant.now().plus(codeExpirationTime),
                    newEmail,
                    user,
                    VerificationCodesType.CHANGE_EMAIL);
            log.debug("Generated change email verification code again for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), code, verificationCode.getCodeExpiresAt(), newVerificationCode.getTargetValue());

            verificationCodeRepository.save(newVerificationCode);
            verificationCodeRepository.flush();
            emailService.sendChangeEmailVerificationEmail(newEmail, code, locale.getLanguage());
            log.info("END send for changing email: change email code resent to email={}", newEmail);
        } catch (ResourceNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during sending email verification code again for email={}", email, ex);
            throw new ServiceException("Database error during sending email verification code again", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during sending email verification code again for email={}", email, ex);
            throw new ServiceException("Unexpected error during sending email verification code again", ex);
        }
    }

    /**
     * <h1>Подтверждение изменения адреса электронной почты пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит текущего пользователя в контексте безопасности Spring Security и его
     *     код подтверждения смены адреса электронной почты. В случае успешной проверки совпадения
     *     и срока действия кода, он удаляется из базы данных, а адрес электронной
     *     почты пользователя обновляется.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден или код подтверждения не найдены,
     *         выбрасывается {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если код подтверждения неверный или переданный адрес электронной почты не
     *         совпадает с сохранённым в базе данных выбрасывается {@link IllegalArgumentException}
     *         с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если код подтверждения истёк, выбрасывается {@link GoneException}
     *         с кодом ответа <b>410</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, сервиса загрузки файлов или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param confirmChangeEmailRequest объект с адресом электронной почты и кодом подтверждения
     * @throws ResourceNotFoundException если пользователь или код подтверждения не найдены
     * @throws IllegalArgumentException если код подтверждения неверный или переданный адрес электронной почты не совпадает с сохранённым
     * @throws GoneException если код подтверждения истёк
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void confirmChangeEmail(ConfirmChangeEmailRequest confirmChangeEmailRequest) {
        UUID ownerUuid = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(ownerUuid)
                    .orElseThrow(() -> {
                        log.warn("Verify changing email error: principal user not found, UUID={}", ownerUuid);
                        return new ResourceNotFoundException("User not found with UUID: " + ownerUuid);
                    });

            VerificationCode verificationCode = verificationCodeRepository
                    .findByUserAndType(user, VerificationCodesType.CHANGE_EMAIL)
                    .orElseThrow(() -> {
                        log.warn("Verify changing email error: code not found for UUID={}", ownerUuid);
                        return new ResourceNotFoundException("code not found");
                    });
            if(!verificationCode.getCode().equals(confirmChangeEmailRequest.getVerificationCode())) {
                log.warn("Verify changing email failed: invalid code for UUID={}", ownerUuid);
                throw new IllegalArgumentException("Invalid confirmation code");
            }
            if(!confirmChangeEmailRequest.getEmail().equals(verificationCode.getTargetValue())) {
                log.warn("Verify changing email failed: email doesn't match, owner UUID={}", ownerUuid);
                throw new IllegalArgumentException("email doesn't match");
            }
            if(verificationCode.getCodeExpiresAt().isBefore(Instant.now())) {
                log.warn("Verify changing email failed: confirmation code expired for UUID={}", ownerUuid);
                throw new GoneException("Verification code expired");
            }

            verificationCodeRepository.delete(verificationCode);
            log.debug("Deleted change email verification code during confirming changing email for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), verificationCode.getCode(), verificationCode.getCodeExpiresAt(), verificationCode.getTargetValue());
            user.setEmail(confirmChangeEmailRequest.getEmail());
            userRepository.save(user);
            log.info("END confirm change email: email was changed successfully, email={}", confirmChangeEmailRequest.getEmail());
        } catch (ResourceNotFoundException | IllegalArgumentException | GoneException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during confirming updating email for UUID={}", ownerUuid, ex);
            throw new ServiceException("Database error during confirming updating email", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during confirming updating email for UUID={}", ownerUuid, ex);
            throw new ServiceException("Unexpected error during confirming updating email", ex);
        }
    }

    /**
     * <h1>Изменение пароля пользователя по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит пользователя по UUID, проверяет переданные пароли и удаляет старый код подтверждение
     *     при существовании. После этого создаёт новый код подтверждения и отправляет его
     *     на адрес электронной почты пользователя, используя {@link EmailMessageProducer}.
     *     Новый пароль сохраняется в базе данных в хэшированном виде
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если старый пароль не совпадает с текущим паролем, старый и новый пароль одинаковые,
     *         введённый пароль не совпадает с повторным паролем или запрос отправлен слишком рано, выбрасывается
     *         {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param id UUID пользователя
     * @param changePasswordRequest объект со старым, новым паролями и подтверждением пароля
     * @param locale объект locale с текущим языком пользователя
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws IllegalArgumentException если старый пароль не совпадает с текущим паролем,
     * старый и новый пароль одинаковые, введённый пароль не совпадает с повторным паролем
     * или запрос отправлен слишком рано
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void changePassword(UUID id, ChangePasswordRequest changePasswordRequest, Locale locale) {
        try {
            User user = userRepository.findByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Changing password error: user not found, UUID={}", id);
                        return new ResourceNotFoundException("User not found");
                    });

            if(!passwordEncoder.matches(changePasswordRequest.getPassword(), user.getPassword())) {
                log.warn("Changing password failed: old password does not match for UUID={}", id);
                throw new IllegalArgumentException("Old password does not match");
            }
            if(changePasswordRequest.getPassword().equals(changePasswordRequest.getNewPassword())) {
                log.warn("Changing password failed: new password is same as old, UUID={}", id);
                throw new IllegalArgumentException("Old and new passwords are same");
            }
            if(!changePasswordRequest.getNewPassword().equals(changePasswordRequest.getConfirmPassword())) {
                log.warn("Changing password failed: passwords do not match for UUID={}", id);
                throw new IllegalArgumentException("Passwords do not match");
            }

            String email = user.getEmail();

            Optional<VerificationCode> oldCode = verificationCodeRepository
                    .findByUserAndType(user, VerificationCodesType.CHANGE_PASSWORD);
            oldCode.ifPresent(i -> {
                Instant sentTime = i.getCodeExpiresAt().minus(codeExpirationTime);
                if(Instant.now().isBefore(sentTime.plus(allowNewCodeTime))) {
                    log.warn("Change password rejected: verification code was requested too recently, UUID={}", id);
                    throw new IllegalArgumentException("Please wait before requesting a new verification code");
                }

                verificationCodeRepository.delete(i);
                verificationCodeRepository.flush();
                log.debug("Deleted change password old verification code during change password request for UUID={}: code={}, expiresAt={}",
                        user.getUuid(), i.getCode(), i.getCodeExpiresAt());
            });

            String code = generateSixDigitCode();
            VerificationCode verificationCode = new VerificationCode(
                    code,
                    Instant.now().plus(codeExpirationTime),
                    passwordEncoder.encode(changePasswordRequest.getNewPassword()),
                    user,
                    VerificationCodesType.CHANGE_PASSWORD);
            log.debug("Generated change password verification code for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), code, verificationCode.getCodeExpiresAt(), email);

            verificationCodeRepository.save(verificationCode);
            emailService.sendChangePasswordVerificationEmail(email, code, locale.getLanguage());
            log.info("END change password request: verification code sent to email={}", email);
        } catch (ResourceNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during updating password for UUID={}", id, ex);
            throw new ServiceException("Database error during updating", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during updating password for UUID={}", id, ex);
            throw new ServiceException("Unexpected error during updating", ex);
        }
    }

    /**
     * <h1>Повторная отправка кода подтверждения смены пароля</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *    Находит пользователя по адресу электронной почты, удаляет его старый код подтверждения
     *    и генерирует новый. Созданный код отправляется в отдельный микросервис
     *    с помощью {@link EmailMessageProducer}
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь или старый код подтверждения не найден, выбрасывается исключение
     *         {@link ResourceNotFoundException} с кодом ошибки <b>404</b>
     *     </li>
     *     <li>
     *         Если запрос отправлен раньше двух минут с момента отправки предыдущего кода,
     *         выбрасывается исключение {@link IllegalArgumentException} с кодом ошибки <b>400</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается исключение
     *         {@link ServiceException} с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param sendCodePasswordRequest объект с адресом почты для повторной отправки кода
     * @param locale объект locale с текущим языком пользователя
     * @throws ResourceNotFoundException если пользователь или прошлый код подтверждения не найден
     * @throws IllegalArgumentException если запрос отправлен слишком рано
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void sendChangePassword(SendCodePasswordRequest sendCodePasswordRequest, Locale locale) {
        String email = sendCodePasswordRequest.getEmail();

        try {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> {
                        log.warn("Sending password verification code again error: user not found, email={}", email);
                        return new ResourceNotFoundException("User not found");
                    });

            VerificationCode verificationCode = verificationCodeRepository.findByUserAndType(user, VerificationCodesType.CHANGE_PASSWORD)
                    .orElseThrow(() -> {
                        log.warn("Sending password verification code again error: previous code not found, email={}", email);
                        return new ResourceNotFoundException("Previous code not found");
                    });

            Instant sentTime = verificationCode.getCodeExpiresAt().minus(codeExpirationTime);
            if(Instant.now().isBefore(sentTime.plus(allowNewCodeTime))) {
                log.warn("Sending password verification code again rejected: too soon, email={}", email);
                throw new IllegalArgumentException("Please wait before requesting a new verification code");
            }

            String newPassword = verificationCode.getTargetValue();

            verificationCodeRepository.delete(verificationCode);
            verificationCodeRepository.flush();
            log.debug("Deleted change password old verification code during sending code again for UUID={}: code={}, expiresAt={}",
                    user.getUuid(), verificationCode.getCode(), verificationCode.getCodeExpiresAt());

            String code = generateSixDigitCode();
            VerificationCode newVerificationCode = new VerificationCode(
                    code,
                    Instant.now().plus(codeExpirationTime),
                    newPassword,
                    user,
                    VerificationCodesType.CHANGE_PASSWORD);
            log.debug("Generated change password verification code again for UUID={}: code={}, expiresAt={}, email={}",
                    user.getUuid(), code, newVerificationCode.getCodeExpiresAt(), user.getEmail());

            verificationCodeRepository.save(newVerificationCode);
            verificationCodeRepository.flush();
            emailService.sendChangePasswordVerificationEmail(email, code, locale.getLanguage());
            log.info("END send for changing password: change password code resent to email={}", email);
        } catch (ResourceNotFoundException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during sending password verification code again for email={}", email, ex);
            throw new ServiceException("Database error during sending password verification code again", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during sending password verification code again for email={}", email, ex);
            throw new ServiceException("Unexpected error during sending password verification code again", ex);
        }
    }

    /**
     * <h1>Подтверждение изменения пароля пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Находит текущего пользователя в контексте безопасности Spring Security и его
     *     код подтверждения смены пароля. В случае успешной проверки совпадения
     *     и срока действия кода, он удаляется из базы данных, а пароль пользователя обновляется.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь или код подтверждения не найдены,
     *         выбрасывается {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         Если код подтверждения или адрес электронной почты неверные,
     *         выбрасывается {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если код подтверждения истёк, выбрасывается {@link GoneException}
     *         с кодом ответа <b>410</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных, сервиса загрузки файлов или любой другой ошибке,
     *         выбрасывается {@link ServiceException} с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param confirmChangePasswordRequest объект с адресом электронной почты и кодом подтверждения
     * @throws ResourceNotFoundException если пользователь или код подтверждения не найдены
     * @throws IllegalArgumentException если код подтверждения или адрес электронной почты неверные
     * @throws GoneException если код подтверждения истёк
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void confirmChangePassword(ConfirmChangePasswordRequest confirmChangePasswordRequest) {
        UUID ownerUuid = authUtil.getPrincipalUuid();

        try {
            User user = userRepository.findByUuid(ownerUuid)
                    .orElseThrow(() -> {
                        log.warn("Verify changing password error: principal user not found, UUID={}", ownerUuid);
                        return new ResourceNotFoundException("User not found");
                    });

            VerificationCode code = verificationCodeRepository
                    .findByUserAndType(user, VerificationCodesType.CHANGE_PASSWORD)
                    .orElseThrow(() -> {
                        log.warn("Verify changing password error: code not found, UUID={}", ownerUuid);
                        return new ResourceNotFoundException("Verification code not found");
                    });
            if(!user.getEmail().equals(confirmChangePasswordRequest.getEmail())) {
                log.warn("Verify changing password failed: email doesn't match, UUID={}", ownerUuid);
                throw new IllegalArgumentException("Invalid email");
            }
            if(!code.getCode().equals(confirmChangePasswordRequest.getVerificationCode())) {
                log.warn("Verify changing password failed: invalid verification code, UUID={}", ownerUuid);
                throw new IllegalArgumentException("Invalid verification code");
            }
            if(code.getCodeExpiresAt().isBefore(Instant.now())) {
                log.warn("Verify changing password failed: verification code is expired, UUID={}", ownerUuid);
                throw new GoneException("Verification code expired");
            }

            user.setPassword(code.getTargetValue());
            verificationCodeRepository.delete(code);
            log.debug("Deleted change password verification code during confirming changing password for UUID={}: code={}, expiresAt={}",
                    user.getUuid(), code.getCode(), code.getCodeExpiresAt());
            userRepository.save(user);
            log.info("END confirm change password: password was changed successfully, UUID={}", ownerUuid);
        } catch (ResourceNotFoundException | IllegalArgumentException | GoneException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during confirming updating password for UUID={}", ownerUuid, ex);
            throw new ServiceException("Database error during confirming updating password", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during confirming updating password for UUID={}", ownerUuid, ex);
            throw new ServiceException("Unexpected error during confirming updating password", ex);
        }
    }

    /**
     * <h1>Удаление пользователя по UUID</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Удаляет пользователя из базы данных по его UUID
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь не найден, выбрасывается {@link ResourceNotFoundException}
     *         с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *      </li>
     * </ul>
     *
     * @param id UUID пользователя
     * @throws ResourceNotFoundException если пользователь не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void deleteUserByUUID(UUID id) {
        try {
            User user = userRepository.findByUuid(id)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with UUID: " + id));

            String avatar = user.getAvatarUrl();

            userRepository.delete(user);
            userRepository.flush();

            if(avatar != null) {
                imageUploaderService.deleteImage(avatar);
            }
            log.info("User was deleted successfully, UUID={}", user.getUuid());
        } catch (ResourceNotFoundException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during deleting user for UUID={}", id, ex);
            throw new ServiceException("Database error during deleting", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during deleting user for UUID={}", id, ex);
            throw new ServiceException("Unexpected error during deleting", ex);
        }
    }

    /**
     * <h1>Переключение подписки на пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Оформляет или отменяет подписку текущего аутентифицированного пользователя
     *     на другого пользователя, переданного UUID.
     * </p>
     *
     * <h3>Исключения:</h3>
     * <ul>
     *     <li>
     *         Если пользователь пытается подписаться на самого себя, выбрасывается
     *         {@link IllegalArgumentException} с кодом ответа <b>400</b>
     *     </li>
     *     <li>
     *         Если пользователь не авторизован, выбрасывается {@link UnauthorizedException}
     *         с кодом ответа <b>401</b>
     *     </li>
     *     <li>
     *         Если пользователь, на которого оформляется подписка, не найден,
     *         выбрасывается {@link ResourceNotFoundException} с кодом ответа <b>404</b>
     *     </li>
     *     <li>
     *         При ошибке базы данных или любой другой ошибке, выбрасывается {@link ServiceException}
     *         с кодом ответа <b>500</b>
     *     </li>
     * </ul>
     *
     * @param id UUID пользователя, на которого оформляется или отменяется подписка
     * @throws IllegalArgumentException если пользователь пытается подписаться на самого себя
     * @throws ResourceNotFoundException если пользователь, на которого оформляется подписка, не найден
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional
    public void toggleSubscription(UUID id) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            if(id.equals(userId)) {
                log.warn("Toggle subscription warn: user tries to subscribe to themselves, UUID={}", userId);
                throw new IllegalArgumentException("You cannot subscribe to yourself");
            }

            User user = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Toggle subscription error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });
            User following = userRepository.findWithLockByUuid(id)
                    .orElseThrow(() -> {
                        log.warn("Toggle subscription warn: user not found with UUID={}", id);
                        return new ResourceNotFoundException("User not found");
                    });

            Subscription subscription = subscriptionRepository
                    .findByFollowerAndFollowing(user, following)
                    .orElseGet(() -> {
                        Subscription newSubscription = new Subscription();
                        newSubscription.setFollower(user);
                        newSubscription.setFollowing(following);
                        newSubscription.setActive(false);
                        return newSubscription;
                    });

            if(!subscription.isActive()) {
                subscription.setActive(true);
                userRepository.incrementSubscribersCount(following.getId());
                userRepository.incrementSubscribesCount(user.getId());
            } else {
                subscription.setActive(false);
                userRepository.decrementSubscribersCount(following.getId());
                userRepository.decrementSubscribesCount(user.getId());
            }

            subscriptionRepository.save(subscription);
        } catch (ResourceNotFoundException | ServiceException | IllegalArgumentException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during toggling subscription, target UUID={}, user UUID={}", id, userId, ex);
            throw new ServiceException("Database error toggling subscription", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during toggling subscription, target UUID={}, user UUID={}", id, userId, ex);
            throw new ServiceException("Unexpected error toggling subscription", ex);
        }
    }

    /**
     * <h1>Получение списка друзей текущего пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Возвращает постраничный список друзей текущего аутентифицированного пользователя —
     *     тех, с кем оформлена взаимная подписка.
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
     * @return страница с данными друзей
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getFriends(Pageable pageable) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User currentUser = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Getting friends error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });

            Page<User> friends = userRepository.findAllFriends(userId, pageable);
            return friends.map(friend -> {
                UserResponse response = modelMapper.map(friend, UserResponse.class);
                response.setSubscribed(
                        subscriptionRepository.findByFollowerAndFollowing(currentUser, friend)
                                .map(Subscription::isActive).orElse(false)
                );
                return response;
            });
        } catch (ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during getting friends, UUID={}", userId, ex);
            throw new ServiceException("Database error getting friends", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during getting friends, UUID={}", userId, ex);
            throw new ServiceException("Unexpected error getting friends", ex);
        }
    }

    /**
     * <h1>Поиск среди друзей текущего пользователя</h1>
     *
     * <h2>Назначение</h2>
     * <p>
     *     Ищет среди друзей текущего аутентифицированного пользователя по имени или имени
     *     пользователя (username). Если запрос начинается с {@code @}, поиск идёт по имени
     *     пользователя, иначе выполняется полнотекстовый поиск по имени.
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
     * @param query поисковый запрос
     * @param pageable параметры пагинации
     * @return страница с найденными друзьями
     * @throws ServiceException если произошла ошибка базы данных или сервера
     */
    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getFriendsBySearch(String query, Pageable pageable) {
        UUID userId = authUtil.getPrincipalUuid();

        try {
            User currentUser = userRepository.findByUuid(userId)
                    .orElseThrow(() -> {
                        log.error("Searching friends error: principal user not found with UUID={}", userId);
                        return new ServiceException("Principal user not found");
                    });

            Pageable unsortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
            String trimmedQuery = query.trim();

            Page<User> friends = trimmedQuery.startsWith("@") ?
                    userRepository.searchFriendsByUsername(trimmedQuery.substring(1), userId, unsortedPageable) :
                    userRepository.searchFriendsFullText(trimmedQuery, userId, unsortedPageable);

            return friends.map(friend -> {
                UserResponse response = modelMapper.map(friend, UserResponse.class);
                response.setSubscribed(
                        subscriptionRepository.findByFollowerAndFollowing(currentUser, friend)
                                .map(Subscription::isActive).orElse(false)
                );
                return response;
            });
        } catch (ServiceException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            log.error("Database error during searching friends, UUID={}", userId, ex);
            throw new ServiceException("Database error searching friends", ex);
        } catch (Exception ex) {
            log.error("Unexpected error during searching friends, UUID={}", userId, ex);
            throw new ServiceException("Unexpected error searching friends", ex);
        }
    }
}
