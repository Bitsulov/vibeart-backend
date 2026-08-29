package ru.vibeart.api.dtos.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "Данные для создания комментария")
public class CommentCreateRequest {
    private UUID postUuid;
    private String text;

    @Schema(description = "UUID публикации", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull(message = "Post UUID cannot be empty")
    public UUID getPostUuid() {
        return postUuid;
    }
    public void setPostUuid(UUID postUuid) {
        this.postUuid = postUuid;
    }

    @Schema(description = "Текст комментария", example = "Отличная работа!")
    @NotBlank(message = "Text cannot be empty")
    @Size(max = 300, message = "Text cannot be longer than 300 symbols")
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
}
