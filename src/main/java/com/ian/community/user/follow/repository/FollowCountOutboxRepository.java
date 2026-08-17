package com.ian.community.user.follow.repository;

import com.ian.community.user.follow.domain.FollowCountOutbox;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FollowCountOutboxRepository
        extends JpaRepository<FollowCountOutbox, UUID> {
    List<FollowCountOutbox>
    findTop100ByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
            LocalDateTime availableAt
    );
}
