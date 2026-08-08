package com.ian.community.post.repository;

import com.ian.community.post.domain.Post;
import com.ian.community.post.domain.PostImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostImageRepository extends JpaRepository<PostImage, Long> {
    Optional<PostImage> findByAuthorPost(Post authorPost);

    List<PostImage> findAllByAuthorPost(Post authorPost);

    List<PostImage> findAllByAuthorPostOrderByDisplayOrderAsc(Post authorPost);

    void deleteByAuthorPost(Post authorPost);

    boolean existsByAuthorPost(Post authorPost);

    boolean existsByMediaAssetMediaId(UUID mediaId);

    long countByMediaAssetIsNull();
}
