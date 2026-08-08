package com.ian.community.common.media;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MediaVariantRepository extends JpaRepository<MediaVariant, Long> {
    List<MediaVariant> findAllByMediaAssetMediaIdOrderByWidthAsc(UUID mediaId);

    List<MediaVariant> findAllByMediaAssetMediaIdAndMediaRevisionOrderByWidthAsc(
            UUID mediaId,
            int mediaRevision
    );

    void deleteAllByMediaAssetMediaIdAndMediaRevision(UUID mediaId, int mediaRevision);

    void deleteAllByMediaAssetMediaId(UUID mediaId);
}
