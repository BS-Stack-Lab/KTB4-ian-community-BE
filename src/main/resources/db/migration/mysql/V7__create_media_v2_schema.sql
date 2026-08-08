CREATE TABLE media_assets (
    media_id BINARY(16) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    purpose VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    frame VARCHAR(30) NOT NULL,
    rotation INT NOT NULL,
    crop_x DECIMAL(12, 9) NOT NULL,
    crop_y DECIMAL(12, 9) NOT NULL,
    crop_width DECIMAL(12, 9) NOT NULL,
    crop_height DECIMAL(12, 9) NOT NULL,
    source_key VARCHAR(500) NOT NULL,
    master_key VARCHAR(500),
    declared_content_type VARCHAR(50) NOT NULL,
    declared_file_size BIGINT NOT NULL,
    source_width INT,
    source_height INT,
    active_revision INT NOT NULL,
    latest_revision INT NOT NULL,
    transform_version INT NOT NULL,
    error_code VARCHAR(80),
    lease_until DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    record_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (media_id),
    CONSTRAINT uk_media_assets_source_key UNIQUE (source_key),
    CONSTRAINT fk_media_assets_owner FOREIGN KEY (owner_user_id) REFERENCES users (user_id),
    INDEX idx_media_assets_owner_status (owner_user_id, status),
    INDEX idx_media_assets_status_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_revisions (
    media_revision_id BIGINT NOT NULL AUTO_INCREMENT,
    media_id BINARY(16) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    frame VARCHAR(30) NOT NULL,
    rotation INT NOT NULL,
    crop_x DECIMAL(12, 9) NOT NULL,
    crop_y DECIMAL(12, 9) NOT NULL,
    crop_width DECIMAL(12, 9) NOT NULL,
    crop_height DECIMAL(12, 9) NOT NULL,
    zoom DECIMAL(6, 3) NOT NULL,
    position_x DECIMAL(12, 9) NOT NULL,
    position_y DECIMAL(12, 9) NOT NULL,
    transform_version INT NOT NULL,
    error_code VARCHAR(80),
    lease_until DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    activated_at DATETIME(6),
    deleted_at DATETIME(6),
    record_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (media_revision_id),
    CONSTRAINT fk_media_revisions_asset FOREIGN KEY (media_id) REFERENCES media_assets (media_id),
    CONSTRAINT uk_media_revisions_asset_revision UNIQUE (media_id, revision),
    INDEX idx_media_revisions_status_lease (status, lease_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_variants (
    media_variant_id BIGINT NOT NULL AUTO_INCREMENT,
    media_id BINARY(16) NOT NULL,
    variant_type VARCHAR(40) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    mime_type VARCHAR(50) NOT NULL,
    file_size BIGINT NOT NULL,
    media_revision INT NOT NULL,
    transform_version INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (media_variant_id),
    CONSTRAINT fk_media_variants_asset FOREIGN KEY (media_id) REFERENCES media_assets (media_id),
    CONSTRAINT uk_media_variants_asset_type_revision_transform
        UNIQUE (media_id, variant_type, media_revision, transform_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE users
    MODIFY COLUMN profile_image VARCHAR(500) NOT NULL,
    ADD COLUMN profile_media_id BINARY(16),
    ADD CONSTRAINT fk_users_profile_media FOREIGN KEY (profile_media_id) REFERENCES media_assets (media_id);

ALTER TABLE post_images
    DROP INDEX uk_post_images_post,
    MODIFY COLUMN image_url VARCHAR(500) NOT NULL,
    ADD COLUMN media_id BINARY(16),
    ADD COLUMN display_order INT NOT NULL DEFAULT 0,
    ADD CONSTRAINT fk_post_images_media FOREIGN KEY (media_id) REFERENCES media_assets (media_id),
    ADD CONSTRAINT uk_post_images_post_order UNIQUE (post_id, display_order);
