package ru.vibeart.api.dtos.album;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vibeart.api.dtos.community.CommunityResponse;
import ru.vibeart.api.dtos.user.UserResponse;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Полные данные альбома")
public class AlbumResponse {
    private UUID uuid;
    private String title;
    private String description;
    private Integer worksCount = 0;
    private UserResponse authorUser;
    private CommunityResponse authorCommunity;
    private String imageUrl;
    private Instant createdAt;

    @Schema(description = "UUID альбома", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Schema(description = "Название альбома", example = "Название альбома")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Описание альбома", example = "Описание альбома")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "Количество публикаций", example = "0")
    public Integer getWorksCount() {
        return worksCount;
    }
    public void setWorksCount(Integer worksCount) {
        this.worksCount = worksCount;
    }

    @Schema(description = "Автор пользователь альбома")
    public UserResponse getAuthorUser() {
        return authorUser;
    }
    public void setAuthorUser(UserResponse authorUser) {
        this.authorUser = authorUser;
    }

    @Schema(description = "Автор сообщество альбома")
    public CommunityResponse getAuthorCommunity() {
        return authorCommunity;
    }
    public void setAuthorCommunity(CommunityResponse authorCommunity) {
        this.authorCommunity = authorCommunity;
    }

    @Schema(description = "Ссылка на изображение альбома", example = "https://storage.vibeart.ru/albums/3fa85f64.jpg")
    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Schema(description = "Дата создания альбома", example = "2026-07-13T10:15:30Z")
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
