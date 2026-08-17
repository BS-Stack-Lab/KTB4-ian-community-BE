ALTER TABLE media_assets ADD COLUMN source_format VARCHAR(20);
ALTER TABLE media_assets ADD COLUMN output_content_type VARCHAR(50);
ALTER TABLE media_assets ADD COLUMN operation_id UUID DEFAULT RANDOM_UUID() NOT NULL;

ALTER TABLE media_revisions ADD COLUMN crop_pixel_width INT;
ALTER TABLE media_revisions ADD COLUMN crop_pixel_height INT;
ALTER TABLE media_revisions ADD COLUMN quality_level VARCHAR(30);
ALTER TABLE media_revisions ADD COLUMN upscale_ratio_1x DECIMAL(8, 4);
ALTER TABLE media_revisions ADD COLUMN operation_id UUID DEFAULT RANDOM_UUID() NOT NULL;

ALTER TABLE post_images ALTER COLUMN image_url DROP NOT NULL;
ALTER TABLE post_images ADD COLUMN pending_media_id UUID;
ALTER TABLE post_images ADD COLUMN pending_revision INT;
ALTER TABLE post_images ADD COLUMN media_state VARCHAR(20) DEFAULT 'READY' NOT NULL;
ALTER TABLE post_images ADD COLUMN media_error_code VARCHAR(80);
ALTER TABLE post_images ADD COLUMN media_operation_id UUID;
ALTER TABLE post_images ADD CONSTRAINT fk_post_images_pending_media
    FOREIGN KEY (pending_media_id) REFERENCES media_assets (media_id);
CREATE INDEX idx_post_images_pending_media_revision
    ON post_images(pending_media_id, pending_revision);
