package ru.vibeart.api.dtos.album;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Часть запроса обновления альбома с JSON данными")
public class AlbumUpdateDetails {
    private String title;
    private String description;

    @Schema(description = "Заголовок альбома", example = "Название альбома")
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 15, message = "Title cannot be longer than 15 symbols")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Описание альбома", example = "Описание альбома")
    @Size(max = 200, message = "Description cannot be longer than 200 symbols")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
}
