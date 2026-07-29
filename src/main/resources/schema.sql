CREATE INDEX IF NOT EXISTS posts_search_idx ON posts
    USING GIN (to_tsvector('russian', title || ' ' || coalesce(description, '')));
    