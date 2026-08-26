package ru.vibeart.api.models.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import ru.vibeart.api.models.enums.TrustStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Сущность сообщества.
 * <p>
 * Содержит профильные данные: название, уникальное имя ({@code username}), описание,
 * аватар ({@code avatarUrl}), дату создания.
 * Хранит денормализованные счётчики {@code worksCount}, {@code subscribersCount}, {@code subscribesCount}.
 * Поддерживает мягкое отключение через флаг {@code enabled} и статус модерации {@code trustStatus}
 * (по умолчанию {@code UNTRUST}).
 * Наследует автоинкрементный {@code id} от {@link BaseEntity}; клиентам передаётся {@code uuid}.
 * </p>
 *
 * <h2>Связи</h2>
 * <ul>
 *   <li>{@link User} — владелец сообщества (ManyToOne);</li>
 *   <li>{@link Album}, {@link Post} — контент сообщества (OneToMany);</li>
 *   <li>{@link CommunitySubscription} — подписчики сообщества (OneToMany);</li>
 *   <li>{@link Tag} — теги сообщества (ManyToMany, владелец связи; join-таблица {@code community_tags});</li>
 *   <li>{@link User} — администраторы сообщества (ManyToMany, владелец связи; join-таблица {@code community_admins}).</li>
 * </ul>
 */
@Entity
@Table(name = "communities")
public class Community extends BaseEntity {
    private UUID uuid;
    private User owner;
    private String name;
    private String username;
    private String description;
    private String avatarUrl;
    private Integer worksCount = 0;
    private Integer subscribersCount = 0;
    private Integer subscribesCount = 0;
    private Instant createdAt;
    private TrustStatus trustStatus = TrustStatus.TRUST;
    private List<Album> albums;
    private List<Post> posts;
    private List<CommunitySubscription> followers;
    private List<Tag> tags;
    private List<User> admins;
    private boolean enabled;

    public Community() {}

    @Column(nullable = false, unique = true)
    public UUID getUuid() {
        return uuid;
    }
    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    @ManyToOne
    @JoinColumn(name = "owner_id", nullable = false)
    public User getOwner() {
        return owner;
    }
    public void setOwner(User owner) {
        this.owner = owner;
    }

    @Column(nullable = false, length = 15)
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    @Column(unique = true, length = 10)
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }

    @Column(length = 200)
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    @Column(nullable = false)
    public Integer getWorksCount() {
        return worksCount;
    }
    public void setWorksCount(Integer worksCount) {
        this.worksCount = worksCount;
    }

    @Column(nullable = false)
    public Integer getSubscribersCount() {
        return subscribersCount;
    }
    public void setSubscribersCount(Integer subscribersCount) {
        this.subscribersCount = subscribersCount;
    }

    @Column(nullable = false)
    public Integer getSubscribesCount() {
        return subscribesCount;
    }
    public void setSubscribesCount(Integer subscribesCount) {
        this.subscribesCount = subscribesCount;
    }

    @Column(nullable = false)
    public Instant getCreatedAt() {
        return createdAt;
    }
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public TrustStatus getTrustStatus() {
        return trustStatus;
    }
    public void setTrustStatus(TrustStatus trustStatus) {
        this.trustStatus = trustStatus;
    }

    @OneToMany(mappedBy = "authorCommunity")
    public List<Album> getAlbums() {
        return albums;
    }
    public void setAlbums(List<Album> albums) {
        this.albums = albums;
    }

    @OneToMany(mappedBy = "authorCommunity")
    public List<Post> getPosts() {
        return posts;
    }
    public void setPosts(List<Post> posts) {
        this.posts = posts;
    }

    @OneToMany(mappedBy = "following")
    public List<CommunitySubscription> getFollowers() {
        return followers;
    }
    public void setFollowers(List<CommunitySubscription> followers) {
        this.followers = followers;
    }

    @ManyToMany
    @JoinTable(
        name = "community_tags",
        joinColumns = @JoinColumn(name = "community_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    public List<Tag> getTags() {
        return tags;
    }
    public void setTags(List<Tag> tags) {
        this.tags = tags;
    }

    @ManyToMany
    @JoinTable(
        name = "community_admins",
        joinColumns = @JoinColumn(name = "community_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    public List<User> getAdmins() {
        return admins;
    }
    public void setAdmins(List<User> admins) {
        this.admins = admins;
    }

    public boolean isEnabled() {
        return enabled;
    }
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
