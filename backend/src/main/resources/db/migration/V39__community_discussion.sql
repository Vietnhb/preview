CREATE TABLE library_comments (
    id UUID PRIMARY KEY,
    item_id UUID NOT NULL REFERENCES library_items(id) ON DELETE CASCADE,
    author_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    body TEXT NOT NULL CHECK (length(btrim(body)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX library_comments_item_created_idx
    ON library_comments (item_id, created_at DESC, id DESC);

CREATE TABLE library_likes (
    item_id UUID NOT NULL REFERENCES library_items(id) ON DELETE CASCADE,
    user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    PRIMARY KEY (item_id, user_id)
);

CREATE INDEX library_likes_user_idx ON library_likes (user_id);
