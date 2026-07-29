# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Команды

```bash
# Сборка
./gradlew build

# Запуск
./gradlew bootRun

# Запуск тестов
./gradlew test

# Запуск одного тестового класса
./gradlew test --tests "ru.vibeart.api.VibeArtBackendApplicationTests"
```

## Настройка окружения

Приложение требует файл `.env` в корне проекта (загружается через `spring.config.import`):

```properties
DB_URL=localhost:5432/vibeart
DB_USERNAME=postgres
DB_PASSWORD=secret
RABBIT_MQ_HOST=localhost
RABBIT_MQ_USERNAME=guest
RABBIT_MQ_PASSWORD=guest
RABBIT_MQ_VHOST=/
JWT_SECRET_KEY=secret
JWT_ACCESS_TIME=900000
JWT_REFRESH_TIME=2592000000
MINIO_URL=http://localhost:9000
MINIO_USERNAME=minioadmin
MINIO_PASSWORD=minioadmin
MINIO_BUCKETNAME=ru-vibeart-images
```

База данных: PostgreSQL. Hibernate автоматически создаёт/обновляет схему при старте (`ddl-auto: update`). `data.sql` наполняет таблицу `roles` записями `USER` и `ADMIN` при каждом запуске (идемпотентные вставки).

## Архитектура

**Стек:** Spring Boot 4.0.1, Java 25, Spring Security + JWT (`jjwt 0.11.5`), Spring Data JPA / Hibernate, PostgreSQL, ModelMapper, Jakarta Validation.

**Структура пакетов:**
- `configs/` — Spring `@Configuration` бины приложения (маппинг DTO, CORS, безопасность, интеграции с внешними сервисами)
- `controllers/` — REST-контроллеры, базовый путь `/api/*`
- `models/entities/` — JPA-сущности (все наследуют `BaseEntity`, которая предоставляет автоинкрементный `id`)
- `models/enums/` — перечисления, используемые как `@Enumerated(EnumType.STRING)` колонки
- `dtos/` — DTO для передачи данных между слоями
- `exceptions/` — кастомные исключения
- `repositories/` — Spring Data JPA репозитории
- `security/` — конфигурация Spring Security, JWT-фильтры
- `services/` — бизнес-логика
- `utils/` — вспомогательные утилиты

**Соглашения по сущностям:**
- Все сущности наследуют `BaseEntity` (суррогатный `Long id`, стратегия `IDENTITY`).
- Поле UUID — внешний идентификатор, передаваемый клиентам; внутренний `id` никогда не раскрывается.
- JPA-аннотации ставятся на **методы-геттеры**, а не на поля.
- Счётчики (`likesCount`, `subscribersCount` и т.д.) — денормализованные колонки, поддерживаемые в коде приложения, а не вычисляемые из таблиц связей.

**Связи доменной модели (ключевые):**
- `User` → `Role` (ManyToOne); записи `Role` засеиваются через `data.sql`
- `User` → `Post`/`Album` (OneToMany, cascade ALL + orphanRemoval)
- `User` ↔ `User` подписки через join-сущность `Subscription` (`follower`/`following`)
- `Post`/`Album` может принадлежать как `User`, так и `Community` в качестве автора
- `Post` ↔ `Tag` и `Album` ↔ `Post` — ManyToMany
- `Community` имеет `CommunitySubscription` для подписчиков
- `User` и `Community` оба имеют `enabled` (мягкое отключение) и `TrustStatus` (модерация)
- `Post` содержит `NSFWStatus` и `AIStatus` для флагов модерации контента
- `Comment` → `Post` + `User` (ManyToOne)
- `Like` → `User` + `Post` (ManyToOne, unique constraint, поле `isActive`)

**Безопасность:** Spring Security подключён.

**Внешние интеграции:** объектное хранилище (MinIO) — для изображений постов, альбомов, аватаров; брокер сообщений (RabbitMQ) — для асинхронной отправки писем внешним email-сервисом; Swagger/OpenAPI — автогенерируемая документация API с авторизацией по JWT.

**Маппинг DTO:** Использовать бин `ModelMapper` из `BeanConfig` для преобразования между сущностями и DTO.

**Важные детали:**
- Запрещено читать .env
- Github Flow. Ветки именуются по функциональности: `feature/<название>`
- Заголовок: не более 50 символов, на английском, начинается с глагола (Add, Fix, Update, Remove)
- Описание: не более 72 символов в строке, на английском, объясняет что и зачем сделано
- Каждый коммит — одна логическая единица изменений
