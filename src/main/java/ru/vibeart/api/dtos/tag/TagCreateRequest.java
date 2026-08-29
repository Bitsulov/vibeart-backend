package ru.vibeart.api.dtos.tag;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Данные для создания тега")
public class TagCreateRequest {
    private String title;

    @Schema(description = "Название тега", example = "landscape")
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 20, message = "Title cannot be longer than 20 symbols")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
}
