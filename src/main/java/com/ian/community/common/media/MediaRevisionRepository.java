package com.ian.community.common.media;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MediaRevisionRepository extends JpaRepository<MediaRevision, Long> {
    Optional<MediaRevision> findByMediaAssetMediaIdAndRevision(UUID mediaId, int revision);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select revision from MediaRevision revision
            where revision.mediaAsset.mediaId = :mediaId and revision.revision = :revision
            """)
    Optional<MediaRevision> findForUpdate(
            @Param("mediaId") UUID mediaId,
            @Param("revision") int revision
    );

    List<MediaRevision> findAllByMediaAssetMediaId(UUID mediaId);

    List<MediaRevision> findAllByActivatedAtIsNullAndStatusInAndUpdatedAtBefore(
            List<MediaStatus> statuses,
            LocalDateTime cutoff
    );
}
