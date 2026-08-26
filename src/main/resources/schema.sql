CREATE INDEX IF NOT EXISTS posts_search_idx ON posts
    USING GIN (to_tsvector('russian', title || ' ' || coalesce(description, '')));

CREATE INDEX IF NOT EXISTS communities_search_idx ON communities
    USING GIN (to_tsvector('russian', name || ' ' || coalesce(description, '')));

CREATE INDEX IF NOT EXISTS users_search_idx ON users
    USING GIN (to_tsvector('russian', coalesce(name, '')));
