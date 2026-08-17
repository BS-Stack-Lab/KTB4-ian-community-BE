package com.ian.community.user.follow.repository;

import com.ian.community.user.follow.domain.UserFollowCount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFollowCountRepository
        extends JpaRepository<UserFollowCount, Long> {
}
