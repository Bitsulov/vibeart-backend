package ru.vibeart.api.exceptions.handlers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import ru.vibeart.api.exceptions.*;
import ru.vibeart.api.exceptions.models.AppError;

import java.time.Instant;

/**
 * Глобальный обработчик исключений REST-контроллеров.
 * <p>
 * Перехватывает кастомные и стандартные исключения, преобразуя их
 * в унифицированный ответ {@link AppError} с соответствующим HTTP-статусом.
 * </p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Ресурс не найден (404).
     *
     * @param ex исключение об отсутствии ресурса
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 404 и описанием ошибки
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<AppError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return buildError(HttpStatus.NOT_FOUND, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Конфликт данных, ресурс уже существует (409).
     *
     * @param ex исключение о конфликте состояния ресурса
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 409 и описанием ошибки
     */
    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<AppError> handleConflict(ConflictException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONFLICT, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Запрашиваемый ресурс устарел навсегда (410).
     *
     * @param ex исключение об устаревшем ресурсе
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 410 и описанием ошибки
     */
    @ExceptionHandler(GoneException.class)
    public ResponseEntity<AppError> handleGone(GoneException ex, HttpServletRequest request) {
        return buildError(HttpStatus.GONE, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Входящий запрос превышает лимит по размеру (413).
     *
     * @param ex исключение о превышении лимита
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 413 и описанием ошибки
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<AppError> handleContentTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return buildError(HttpStatus.CONTENT_TOO_LARGE, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Неверные данные от клиента (400).
     *
     * @param ex исключение с описанием некорректных данных
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 400 и описанием ошибки
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<AppError> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return buildError(HttpStatus.BAD_REQUEST, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Нет прав для получения доступа (403).
     *
     * @param ex исключение об отсутствии прав доступа
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 403 и описанием ошибки
     */
    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    public ResponseEntity<AppError> handleForbidden(ForbiddenException ex, HttpServletRequest request) {
        return buildError(HttpStatus.FORBIDDEN, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Пользователь не авторизован (401).
     *
     * @param ex исключение о неудачной аутентификации
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 401 и описанием ошибки
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<AppError> handleUnauthorized(UnauthorizedException ex, HttpServletRequest request) {
        return buildError(HttpStatus.UNAUTHORIZED, ex.getMessage(), request.getRequestURI());
    }

    /**
     * Ошибка базы данных с обезличенным сообщением (500).
     *
     * @param ex исключение доступа к данным
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 500 и обезличенным сообщением об ошибке
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AppError> handleDatabase(DataAccessException ex, HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Database error occurred", request.getRequestURI());
    }

    /**
     * Неверное значение параметра (400).
     *
     * @param ex исключение о несоответствии типа параметра запроса
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 400 и описанием некорректного параметра
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<AppError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    /**
     * Отсутствует обязательный параметр запроса (400).
     *
     * @param ex исключение об отсутствующем параметре запроса
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 400 и описанием отсутствующего параметра
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<AppError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String message = "Required request parameter \"" + ex.getParameterName() + "\" is not present";
        return buildError(HttpStatus.BAD_REQUEST, message, request.getRequestURI());
    }

    /**
     * Ошибка валидации (400).
     *
     * @param ex исключение с результатами валидации тела запроса
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 400 и описанием первой ошибки валидации
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AppError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation error");
        return buildError(HttpStatus.BAD_REQUEST, errorMessage, request.getRequestURI());
    }

    /**
     * Остальные необработанные ошибки (500).
     *
     * @param ex произвольное исключение
     * @param request текущий HTTP-запрос
     * @return ответ с кодом 500 и сообщением исключения
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<AppError> handleAllUnhandled(Exception ex, HttpServletRequest request) {
        return buildError(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error occurred: " + ex.getMessage(), request.getRequestURI());
    }

    /**
     * Формирует тело ответа с описанием ошибки.
     *
     * @param status HTTP-статус ответа
     * @param message сообщение об ошибке
     * @param path путь запроса, на котором произошла ошибка
     * @return объект {@link ResponseEntity} с {@link AppError} и заданным статусом
     */
    private ResponseEntity<AppError> buildError(HttpStatus status, String message, String path) {
        AppError error = new AppError(
                status.value(),
                message,
                path,
                Instant.now()
        );
        return new ResponseEntity<>(error, status);
    }
}
