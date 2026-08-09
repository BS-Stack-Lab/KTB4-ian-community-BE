package com.ian.community.post.repository;

import com.ian.community.post.domain.Post;
import com.ian.community.user.domain.User;
import com.ian.community.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
public class PostRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("피드 목록은 트랜잭션 종료 후 응답 변환에 필요한 작성자를 함께 조회한다")
    void feedListFetchesAuthor() {
        User user = userRepository.saveAndFlush(
                new User(
                        "feed-author-fetch@example.com",
                        "encoded-password",
                        "피드작성자"
                )
        );
        postRepository.saveAndFlush(new Post(user, "작성자 조회 피드"));
        entityManager.clear();

        Post post = postRepository
                .findAllByPostDeletedFalseOrderByCreatedAtDescPostIdDesc(
                        PageRequest.of(0, 10)
                )
                .getContent()
                .getFirst();

        assertThat(Hibernate.isInitialized(post.getAuthorUser())).isTrue();
        assertThat(post.getAuthorUser().getUserId()).isEqualTo(user.getUserId());
    }

    @Test
    @DisplayName("미디어 참조 여부 쿼리는 PostImage 식별자 속성을 정상 해석한다")
    void checksWhetherMediaIsReferencedByPostImage() {
        assertThat(postImageRepository.existsByMediaAssetMediaId(UUID.randomUUID())).isFalse();
    }
}
