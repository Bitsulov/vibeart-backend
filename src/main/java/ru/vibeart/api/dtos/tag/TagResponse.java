package ru.vibeart.api.dtos.tag;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "Данные тега для отображения")
public class TagResponse {
    private String title;
    private Instant createdAt;

    @Schema(description = "Название тега", example = "landscape")
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Schema(description = "Дата создания тега", example = "2026-07-13T10:15:30Z")
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
