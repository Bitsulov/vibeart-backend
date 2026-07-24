package ru.vibeart.api.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Сущность лайка пользователя к посту.
 * <p>
 * Хранит пару «пользователь ({@link User}) → пост ({@link Post})» и флаг активности {@code isActive},
 * который позволяет снимать лайк без удаления записи из БД.
 * Уникальное ограничение на пару {@code (user_id, post_id)} исключает дублирование.
 * Наследует автоинкрементный {@code id} от {@link BaseEntity}.
 * </p>
 */
@Entity
@Table(
    name = "likes",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"})
)
public class Like extends BaseEntity {
    private boolean isActive;
    private User user;
    private Post post;

    public Like() {}

    public Like(boolean isActive, User user, Post post) {
        this.isActive = isActive;
        this.user = user;
        this.post = post;
    }

    public boolean isActive() {
        return isActive;
    }
    public void setActive(boolean active) {
        isActive = active;
    }

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

    @ManyToOne
    @JoinColumn(name = "post_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    public Post getPost() {
        return post;
    }
    public void setPost(Post post) {
        this.post = post;
    }
}
