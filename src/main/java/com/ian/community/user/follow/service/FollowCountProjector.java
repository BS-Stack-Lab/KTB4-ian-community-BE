package com.ian.community.user.follow.service;

import com.ian.community.user.follow.repository.FollowCountOutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FollowCountProjector {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final FollowCountProjectionTransactions transactions;
    private final FollowCountOutboxRepository outboxRepository;

    @Scheduled(fixedDelayString = "${app.follow.count-projector-delay:PT1S}")
    public void project() {
        List<UUID> dueEventIds = outboxRepository
                .findTop100ByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        LocalDateTime.now(SERVICE_ZONE)
                )
                .stream()
                .map(event -> event.getEventId())
                .toList();
        if (dueEventIds.isEmpty()) {
            return;
        }
        try {
            transactions.processPending();
        } catch (RuntimeException exception) {
            log.warn("팔로우 카운트 projection 처리에 실패했습니다.", exception);
            transactions.reschedule(dueEventIds, exception.getMessage());
        }
    }
}
