package com.ian.community.common.media;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select media from MediaAsset media where media.mediaId = :mediaId")
    Optional<MediaAsset> findByIdForUpdate(@Param("mediaId") UUID mediaId);
}
