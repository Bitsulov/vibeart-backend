package ru.vibeart.api.dtos.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Данные для изменения комментария")
public class CommentUpdateRequest {
    private String text;

    @Schema(description = "Новый текст комментария", example = "Отличная работа!")
    @NotBlank(message = "Text cannot be empty")
    @Size(max = 300, message = "Text cannot be longer than 300 symbols")
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
    }
}
