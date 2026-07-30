package ru.vibeart.api.dtos.album;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос добавления публикаций в альбом")
public class AlbumAddPostsRequest {
    private List<UUID> postsUUIDs;

    @Schema(description = "Список UUID публикаций для добавления в альбом", example = "[\"3fa85f64-5717-4562-b3fc-2c963f66afa6\"]")
    @NotEmpty(message = "Posts UUID list cannot be empty")
    public List<UUID> getPostsUUIDs() {
        return postsUUIDs;
    }
    public void setPostsUUIDs(List<UUID> postsUUIDs) {
        this.postsUUIDs = postsUUIDs;
    }
}
