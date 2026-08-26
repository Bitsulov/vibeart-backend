package ru.vibeart.api.dtos.community;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Часть запроса создания сообщества с JSON данными")
public class CommunityCreateDetails {
    private String title;
    private String description;
    private String username;
    private List<String> tagsTitles;
    private List<UUID> adminsUuids;
    private UUID userId;

    @Schema(description = "Заголовок сообщества", example = "Название сообщества")
    @Size(min = 3, max = 15, message = "Title cannot be shorter than 3 symbols and longer than 15 symbols")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Описание сообщества", example = "Описание сообщества")
    @Size(max = 200, message = "Description cannot be longer than 200 symbols")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "Имя пользователя сообщества", example = "username")
    @Size(min = 2, max = 10, message = "Username cannot be shorter than 2 and longer than 10 symbols")
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    @Schema(description = "Список названий тегов сообщества", example = "[\"landscape\", \"portrait\"]")
    @NotNull(message = "Tags cannot be empty")
    public List<@NotBlank(message = "Tag cannot be empty") String> getTagsTitles() {
        return tagsTitles;
    }
    public void setTagsTitles(List<String> tagsTitles) {
        this.tagsTitles = tagsTitles;
    }

    @Schema(description = "Список UUID администраторов сообщества", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    @NotNull(message = "Admins UUIDs cannot be empty")
    public List<@NotNull(message = "Admin UUID cannot be empty") UUID> getAdminsUuids() {
        return adminsUuids;
    }
    public void setAdminsUuids(List<UUID> adminsUuids) {
        this.adminsUuids = adminsUuids;
    }

    @Schema(description = "UUID пользователя", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull(message = "User UUID cannot be empty")
    public UUID getUserId() {
        return userId;
    }
    public void setUserId(UUID userId) {
        this.userId = userId;
    }
}
