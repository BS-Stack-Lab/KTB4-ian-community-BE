package com.ian.community.user.repository;

import com.ian.community.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByEmailAndUserDeletedFalse(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    boolean existsByProfileMediaMediaId(UUID mediaId);

    long countByProfileMediaIsNull();
}
