package ru.vibeart.api.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.Instant;
import java.util.UUID;

/**
 * Сущность комментария к посту.
 * <p>
 * Содержит текст комментария и дату создания.
 * Наследует автоинкрементный {@code id} от {@link BaseEntity}.
 * </p>
 *
 * <h2>Связи</h2>
 * <ul>
 *   <li>{@link Post} — пост, к которому оставлен комментарий (ManyToOne);</li>
 *   <li>{@link User} — автор комментария (ManyToOne).</li>
 * </ul>
 */
@Entity
@Table(name = "comments")
public class Comment extends BaseEntity {
    private UUID uuid;
    private String text;
    private Post post;
    private User author;
    private Instant createdAt;

    public Comment() {}

    @Column(nullable = false, unique = true)
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Column(nullable = false, length = 300)
    public String getText() {
        return text;
    }
    public void setText(String text) {
        this.text = text;
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

    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    public User getAuthor() {
        return author;
    }
    public void setAuthor(User author) {
        this.author = author;
    }

    @Column(nullable = false)
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
