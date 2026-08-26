package ru.vibeart.api.dtos.user;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vibeart.api.models.enums.OnlineStatus;
import ru.vibeart.api.models.enums.TrustStatus;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Данные пользователя для отображения")
public class UserResponse {
    private UUID uuid;
    private String name;
    private String username;
    private String description;
    private String avatarUrl;
    private Integer worksCount;
    private Integer subscribersCount;
    private Integer subscribesCount;
    private Instant createdAt;
    private TrustStatus trustStatus;
    private OnlineStatus onlineStatus;
    private boolean enabled;
    private Boolean subscribed;

    @Schema(description = "UUID пользователя", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Schema(description = "Имя пользователя", example = "Иван")
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Schema(description = "Псевдоним пользователя", example = "ivan")
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    @Schema(description = "Описание пользователя", example = "Длинное описание")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "Ссылка на аватар пользователя", example = "http://host:9000/bucket/path/file.jpg")
    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Schema(description = "Количество постов", example = "100")
    public Integer getWorksCount() {
        return worksCount;
    }
    public void setWorksCount(Integer worksCount) {
        this.worksCount = worksCount;
    }

    @Schema(description = "Количество подписчиков", example = "1000")
    public Integer getSubscribersCount() {
        return subscribersCount;
    }
    public void setSubscribersCount(Integer subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    @Schema(description = "Количество подписок", example = "10")
    public Integer getSubscribesCount() {
        return subscribesCount;
    }
    public void setSubscribesCount(Integer subscribesCount) {
        this.subscribesCount = subscribesCount;
    }

    @Schema(description = "Дата создания поста", example = "2024-01-15T12:30:00Z")
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Schema(description = "Статус доверия", example = "trust")
    public TrustStatus getTrustStatus() {
        return trustStatus;
    }
    public void setTrustStatus(TrustStatus trustStatus) {
        this.trustStatus = trustStatus;
    }

    @Schema(description = "Онлайн статус", example = "online")
    public OnlineStatus getOnlineStatus() {
        return onlineStatus;
    }
    public void setOnlineStatus(OnlineStatus onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    @Schema(description = "Признак активного аккаунта", example = "true")
    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Schema(description = "Подписан ли текущий пользователь на этого пользователя.")
    public Boolean getSubscribed() {
        return subscribed;
    }
    public void setSubscribed(Boolean subscribed) {
        this.subscribed = subscribed;
    }
}
