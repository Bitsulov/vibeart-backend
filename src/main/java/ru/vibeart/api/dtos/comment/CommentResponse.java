package ru.vibeart.api.dtos.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.vibeart.api.dtos.user.UserResponse;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Данные комментария для отображения")
public class CommentResponse {
    private UUID uuid;
    private String text;
    private Instant createdAt;
    private UserResponse author;

    @Schema(description = "UUID комментария", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Schema(description = "Текст комментария", example = "Отличная работа!")
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }

    @Schema(description = "Дата создания комментария", example = "2026-07-13T10:15:30Z")
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Schema(description = "Данные автора комментария")
    public UserResponse getAuthor() {
        return author;
    }
    public void setAuthor(UserResponse author) {
        this.author = author;
    }
}
