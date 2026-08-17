ALTER TABLE media_assets
    ADD COLUMN source_format VARCHAR(20),
    ADD COLUMN output_content_type VARCHAR(50),
    ADD COLUMN operation_id BINARY(16);

UPDATE media_assets SET operation_id = UUID_TO_BIN(UUID()) WHERE operation_id IS NULL;
ALTER TABLE media_assets MODIFY COLUMN operation_id BINARY(16) NOT NULL;

ALTER TABLE media_revisions
    ADD COLUMN crop_pixel_width INT,
    ADD COLUMN crop_pixel_height INT,
    ADD COLUMN quality_level VARCHAR(30),
    ADD COLUMN upscale_ratio_1x DECIMAL(8, 4),
    ADD COLUMN operation_id BINARY(16);

UPDATE media_revisions SET operation_id = UUID_TO_BIN(UUID()) WHERE operation_id IS NULL;
ALTER TABLE media_revisions MODIFY COLUMN operation_id BINARY(16) NOT NULL;

ALTER TABLE post_images
    MODIFY COLUMN image_url VARCHAR(500) NULL,
    ADD COLUMN pending_media_id BINARY(16),
    ADD COLUMN pending_revision INT,
    ADD COLUMN media_state VARCHAR(20) NOT NULL DEFAULT 'READY',
    ADD COLUMN media_error_code VARCHAR(80),
    ADD COLUMN media_operation_id BINARY(16),
    ADD CONSTRAINT fk_post_images_pending_media
        FOREIGN KEY (pending_media_id) REFERENCES media_assets (media_id),
    ADD INDEX idx_post_images_pending_media_revision
        (pending_media_id, pending_revision);
