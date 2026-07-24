package ru.vibeart.api.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ru.vibeart.api.models.enums.AIStatus;
import ru.vibeart.api.models.enums.NSFWStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сущность публикации (поста) в приложении.
 * <p>
 * Пост может принадлежать пользователю ({@link User}) или сообществу ({@link Community}).
 * Наследует автоинкрементный {@code id} от {@link BaseEntity}.
 * </p>
 *
 * <h2>Модерация</h2>
 * <ul>
 *   <li>{@link ru.vibeart.api.models.enums.NSFWStatus} — флаг проверки на 18+ контент;</li>
 *   <li>{@link ru.vibeart.api.models.enums.AIStatus} — флаг проверки на AI-сгенерированный контент.</li>
 * </ul>
 *
 * <h2>Связи</h2>
 * <ul>
 *   <li>{@link Tag} — теги поста (ManyToMany, владелец связи; join-таблица {@code post_tags});</li>
 *   <li>{@link Album} — альбомы, в которые включён пост (ManyToMany, обратная сторона);</li>
 *   <li>{@link Like} — лайки поста (OneToMany, cascade ALL + orphanRemoval — удаляются вместе с постом);</li>
 *   <li>{@link Report} — жалобы на пост (OneToMany, cascade ALL + orphanRemoval — удаляются вместе с постом);</li>
 *   <li>{@link Comment} — комментарии к посту (OneToMany, cascade ALL + orphanRemoval — удаляются вместе с постом).</li>
 * </ul>
 */
@Entity
@Table(name = "posts")
public class Post extends BaseEntity {
    private UUID uuid;
    private String title;
    private String description;
    private Long likesCount = 0L;
    private Long commentsCount = 0L;
    private Long reportsCount = 0L;
    private NSFWStatus nsfwStatus = NSFWStatus.UNCHECKED;
    private AIStatus aiStatus = AIStatus.GOOD;
    private String imageUrl;
    private Instant createdAt;
    private User authorUser;
    private Community authorCommunity;
    private List<Tag> tags;
    private List<Album> albums;
    private List<Like> likes;
    private List<Report> reports;
    private List<Comment> comments;

    public Post() {}

    @Column(nullable = false, unique = true)
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @Column(nullable = false, length = 15)
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }

    @Column(length = 200)
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    @Column(nullable = false)
    public Long getLikesCount() {
        return likesCount;
    }
    public void setLikesCount(Long likesCount) {
        this.likesCount = likesCount;
    }

    @Column(nullable = false)
    public Long getCommentsCount() {
        return commentsCount;
    }
    public void setCommentsCount(Long commentsCount) {
        this.commentsCount = commentsCount;
    }

    @Column(nullable = false)
    public Long getReportsCount() {
        return reportsCount;
    }
    public void setReportsCount(Long reportsCount) {
        this.reportsCount = reportsCount;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public NSFWStatus getNsfwStatus() {
        return nsfwStatus;
    }
    public void setNsfwStatus(NSFWStatus nsfwStatus) {
        this.nsfwStatus = nsfwStatus;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AIStatus getAiStatus() {
        return aiStatus;
    }
    public void setAiStatus(AIStatus aiStatus) {
        this.aiStatus = aiStatus;
    }

    @Column(nullable = false)
    public String getImageUrl() {
        return imageUrl;
    }
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    @Column(nullable = false)
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @ManyToOne
    @JoinColumn(name = "author_user_id")
    public User getAuthorUser() {
        return authorUser;
    }
    public void setAuthorUser(User authorUser) {
        this.authorUser = authorUser;
    }

    @ManyToOne
    @JoinColumn(name = "author_community_id")
    public Community getAuthorCommunity() {
        return authorCommunity;
    }
    public void setAuthorCommunity(Community authorCommunity) {
        this.authorCommunity = authorCommunity;
    }

    @ManyToMany
    @JoinTable(
        name = "post_tags",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    public List<Tag> getTags() {
        return tags;
    }
    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @ManyToMany(mappedBy = "posts")
    public List<Album> getAlbums() {
        return albums;
    }
    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Like> getLikes() {
        return likes;
    }
    public void setLikes(List<Like> likes) {
        this.likes = likes;
    }

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Report> getReports() {
        return reports;
    }
    public void setReports(List<Report> reports) {
        this.reports = reports;
    }

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<Comment> getComments() {
        return comments;
    }
    public void setComments(List<Comment> comments) {
        this.comments = comments;
    }
}
