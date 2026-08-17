CREATE TABLE user_follows (
    follow_id BIGINT NOT NULL AUTO_INCREMENT,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (follow_id),
    CONSTRAINT uk_user_follows_follower_following
        UNIQUE (follower_id, following_id),
    CONSTRAINT chk_user_follows_not_self
        CHECK (follower_id <> following_id),
    CONSTRAINT fk_user_follows_follower
        FOREIGN KEY (follower_id) REFERENCES users (user_id),
    CONSTRAINT fk_user_follows_following
        FOREIGN KEY (following_id) REFERENCES users (user_id),
    INDEX idx_user_follows_following_created
        (following_id, created_at DESC, follow_id DESC),
    INDEX idx_user_follows_follower_created
        (follower_id, created_at DESC, follow_id DESC)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_follow_counts (
    user_id BIGINT NOT NULL,
    follower_count BIGINT NOT NULL DEFAULT 0,
    following_count BIGINT NOT NULL DEFAULT 0,
    projection_version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6),
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_follow_counts_user
        FOREIGN KEY (user_id) REFERENCES users (user_id),
    CONSTRAINT chk_user_follow_counts_non_negative
        CHECK (follower_count >= 0 AND following_count >= 0)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE follow_count_outbox (
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6),
    last_error VARCHAR(500),
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_follow_count_outbox_poll
        (processed_at, available_at, created_at)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE INDEX idx_posts_user_deleted_created
    ON posts (user_id, post_deleted, created_at DESC, post_id DESC);

INSERT INTO user_follow_counts (
    user_id,
    follower_count,
    following_count,
    projection_version,
    updated_at
)
SELECT user_id, 0, 0, 0, CURRENT_TIMESTAMP(6)
FROM users
WHERE user_deleted = FALSE;
