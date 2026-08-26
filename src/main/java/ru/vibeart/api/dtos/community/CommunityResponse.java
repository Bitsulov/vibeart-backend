package ru.vibeart.api.dtos.community;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vibeart.api.dtos.user.UserResponse;
import ru.vibeart.api.models.enums.TrustStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Данные сообщества для отображения")
public class CommunityResponse {
    private UUID uuid;
    private UserResponse owner;
    private String name;
    private String username;
    private String description;
    private String avatarUrl;
    private Integer worksCount;
    private Integer subscribersCount;
    private Integer subscribesCount;
    private Instant createdAt;
    private TrustStatus trustStatus;
    private List<UserResponse> admins;
    private List<String> tags;
    private Boolean isSubscribed;

    @Schema(description = "UUID сообщества", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Schema(description = "Автор сообщества")
    public UserResponse getOwner() {
        return owner;
    }
    public void setOwner(UserResponse owner) {
        this.owner = owner;
    }

    @Schema(description = "Название сообщества", example = "title")
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Schema(description = "Имя пользователя сообщества", example = "username")
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    @Schema(description = "Описание сообщества", example = "Сообщество художников")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "Адрес аватара сообщества", example = "https://storage.vibeart.ru/communities/3fa85f64.jpg")
    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Schema(description = "Количество работ сообщества", example = "0")
    public Integer getWorksCount() {
        return worksCount;
    }
    public void setWorksCount(Integer worksCount) {
        this.worksCount = worksCount;
    }

    @Schema(description = "Количество подписчиков сообщества", example = "0")
    public Integer getSubscribersCount() {
        return subscribersCount;
    }
    public void setSubscribersCount(Integer subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    @Schema(description = "Количество подписок сообщества", example = "0")
    public Integer getSubscribesCount() {
        return subscribesCount;
    }
    public void setSubscribesCount(Integer subscribesCount) {
        this.subscribesCount = subscribesCount;
    }

    @Schema(description = "Дата создания сообщества", example = "2026-07-13T10:15:30Z")
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Schema(description = "Статус доверия сообщества", example = "trust")
    public TrustStatus getTrustStatus() {
        return trustStatus;
    }
    public void setTrustStatus(TrustStatus trustStatus) {
        this.trustStatus = trustStatus;
    }

    @Schema(description = "Список администраторов сообщества")
    public List<UserResponse> getAdmins() {
        return admins;
    }
    public void setAdmins(List<UserResponse> admins) {
        this.admins = admins;
    }

    @Schema(description = "Список названий тегов сообщества", example = "[\"landscape\", \"portrait\"]")
    public List<String> getTags() {
        return tags;
    }
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Schema(description = "Подписан ли текущий пользователь на это сообщество.")
    public Boolean isSubscribed() {
        return isSubscribed;
    }
    public void setSubscribed(Boolean subscribed) {
        isSubscribed = subscribed;
    }
}
