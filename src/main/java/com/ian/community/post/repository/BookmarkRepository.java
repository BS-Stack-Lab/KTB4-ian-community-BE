package com.ian.community.post.repository;

import com.ian.community.post.domain.Bookmark;
import com.ian.community.post.domain.Post;
import com.ian.community.user.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

    Optional<Bookmark> findByUserAndPost(
            User user,
            Post post
    );

    boolean existsByUserAndPost(
            User user,
            Post post
    );

    @EntityGraph(attributePaths = {"post", "post.authorUser", "post.authorUser.profileMedia"})
    Slice<Bookmark> findAllByUserAndPost_PostDeletedFalseOrderByCreatedAtDescBookmarkIdDesc(
            User user,
            Pageable pageable
    );

    @Query("""
            select bookmark.post.postId
            from Bookmark bookmark
            where bookmark.user.userId = :userId
              and bookmark.post.postId in :postIds
              and bookmark.post.postDeleted = false
            """)
    java.util.List<Long> findBookmarkedPostIds(
            @Param("userId") Long userId,
            @Param("postIds") java.util.Collection<Long> postIds
    );
}
