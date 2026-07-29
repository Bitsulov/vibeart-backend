package ru.vibeart.api.dtos.post;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "Часть запроса создания публикации с JSON данными")
public class PostCreateDetails {
    private String title;
    private String description;
    private UUID authorUuid;
    private List<String> tagsTitles;

    @Schema(description = "Заголовок публикации", example = "Название")
    @NotBlank(message = "Title cannot be empty")
    @Size(max = 15, message = "Title cannot be longer than 15 symbols")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Описание публикации", example = "Описание публикации")
    @Size(max = 200, message = "Description cannot be longer than 200 symbols")
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Schema(description = "UUID автора (пользователь или сообщество)", example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
    @NotNull(message = "Author UUID cannot be empty")
    public UUID getAuthorUuid() {
        return authorUuid;
    }
    public void setAuthorUuid(UUID authorUuid) {
        this.authorUuid = authorUuid;
    }

    @Schema(description = "Список названий тегов публикации", example = "[\"landscape\", \"portrait\"]")
    @NotNull(message = "Tags cannot be empty")
    public List<@NotBlank(message = "Tag cannot be empty") String> getTagsTitles() {
        return tagsTitles;
    }
    public void setTagsTitles(List<String> tagsTitles) {
        this.tagsTitles = tagsTitles;
    }
}
