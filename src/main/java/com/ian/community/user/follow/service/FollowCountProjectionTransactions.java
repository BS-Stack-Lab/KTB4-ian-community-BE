package com.ian.community.user.follow.service;

import com.ian.community.user.follow.domain.FollowCountOutbox;
import com.ian.community.user.follow.domain.UserFollowCount;
import com.ian.community.user.follow.repository.FollowCountOutboxRepository;
import com.ian.community.user.follow.repository.UserFollowCountRepository;
import com.ian.community.user.follow.repository.UserFollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FollowCountProjectionTransactions {
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final FollowCountOutboxRepository outboxRepository;
    private final UserFollowRepository userFollowRepository;
    private final UserFollowCountRepository userFollowCountRepository;

    @Transactional
    public int processPending() {
        List<FollowCountOutbox> events = outboxRepository
                .findTop100ByProcessedAtIsNullAndAvailableAtLessThanEqualOrderByCreatedAtAsc(
                        LocalDateTime.now(SERVICE_ZONE)
                );
        if (events.isEmpty()) {
            return 0;
        }

        LinkedHashSet<Long> affectedUserIds = new LinkedHashSet<>();
        events.forEach(event -> {
            affectedUserIds.add(event.getFollowerId());
            affectedUserIds.add(event.getFollowingId());
        });
        reconcile(affectedUserIds);
        events.forEach(FollowCountOutbox::markProcessed);
        return events.size();
    }

    @Transactional
    public void reconcile(Collection<Long> userIds) {
        userIds.forEach(this::replaceWithAuthoritativeCount);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reschedule(List<UUID> eventIds, String error) {
        outboxRepository.findAllById(eventIds)
                .stream()
                .filter(event -> event.getProcessedAt() == null)
                .forEach(event -> event.reschedule(error));
    }

    private void replaceWithAuthoritativeCount(Long userId) {
        UserFollowCount count = userFollowCountRepository.findById(userId)
                .orElseGet(() -> new UserFollowCount(userId));
        count.replace(
                userFollowRepository.countActiveFollowers(userId),
                userFollowRepository.countActiveFollowing(userId)
        );
        userFollowCountRepository.save(count);
    }
}
