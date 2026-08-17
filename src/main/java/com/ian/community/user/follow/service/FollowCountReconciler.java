package com.ian.community.user.follow.service;

import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FollowCountReconciler {
    private final UserRepository userRepository;
    private final FollowCountProjectionTransactions transactions;

    @Scheduled(fixedDelayString = "${app.follow.reconciliation-delay:PT5M}")
    public void reconcile() {
        transactions.reconcile(userRepository.findActiveUserIds());
    }
}
