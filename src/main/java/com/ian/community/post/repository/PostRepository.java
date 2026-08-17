package com.ian.community.post.repository;

import com.ian.community.post.domain.Post;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.Optional;

@Repository
public interface PostRepository extends JpaRepository<Post, Long>  {
    @EntityGraph(attributePaths = {"authorUser", "authorUser.profileMedia"})
    Optional<Post> findByPostIdAndPostDeletedFalse(Long postId); // 삭제되지 않은 게시글 단건 조회

    @EntityGraph(attributePaths = {"authorUser", "authorUser.profileMedia"})
    Slice<Post> findAllByPostDeletedFalseOrderByCreatedAtDescPostIdDesc(Pageable pageable);

    @EntityGraph(attributePaths = {"authorUser", "authorUser.profileMedia"})
    Slice<Post> findAllByAuthorUser_UserIdAndPostDeletedFalseOrderByCreatedAtDescPostIdDesc(
            Long userId,
            Pageable pageable
    );

    boolean existsByPostIdAndPostDeletedFalse(Long postId); // 삭제되지 않은 게시글 존재 여부 확인
}
