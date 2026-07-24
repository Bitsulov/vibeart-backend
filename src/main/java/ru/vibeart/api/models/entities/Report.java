package ru.vibeart.api.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Сущность жалобы пользователя на пост.
 * <p>
 * Хранит пару «пользователь ({@link User}) → пост ({@link Post})».
 * Уникальное ограничение на пару {@code (user_id, post_id)} исключает повторную жалобу
 * от одного и того же пользователя на один и тот же пост.
 * Наследует автоинкрементный {@code id} от {@link BaseEntity}.
 * </p>
 */
@Entity
@Table(
    name = "reports",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "post_id"})
)
public class Report extends BaseEntity {
    private User user;
    private Post post;

    public Report() {}

    public Report(User user, Post post) {
        this.user = user;
        this.post = post;
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
