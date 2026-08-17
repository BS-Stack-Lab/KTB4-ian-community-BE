package com.ian.community.user.follow;

import com.ian.community.post.domain.Post;
import com.ian.community.post.repository.PostRepository;
import com.ian.community.security.jwt.JwtCookieProvider;
import com.ian.community.security.token.TokenService;
import com.ian.community.user.domain.User;
import com.ian.community.user.follow.repository.FollowCountOutboxRepository;
import com.ian.community.user.follow.repository.UserFollowRepository;
import com.ian.community.user.follow.service.FollowCountProjectionTransactions;
import com.ian.community.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.task.scheduling.enabled=false")
@AutoConfigureMockMvc
@Transactional
class UserProfileFollowIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserFollowRepository userFollowRepository;

    @Autowired
    private FollowCountOutboxRepository outboxRepository;

    @Autowired
    private FollowCountProjectionTransactions projectionTransactions;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("프로필 상태는 Self와 Other 관계를 구분하고 팔로우 요청은 멱등이다")
    void profileTypeAndIdempotentFollow() throws Exception {
        User viewer = saveUser("profile-viewer@example.com", "viewer");
        User target = saveUser("profile-target@example.com", "target");
        Cookie access = accessCookie(viewer);

        mockMvc.perform(get("/api/v2/users/{userId}/profile", viewer.getUserId())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileType").value("SELF"));

        mockMvc.perform(get("/api/v2/users/{userId}/profile", target.getUserId())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileType")
                        .value("OTHER_NOT_FOLLOWING"));

        mockMvc.perform(post("/api/v2/users/{userId}/followers/me", target.getUserId())
                        .with(csrf())
                        .cookie(access))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("FOLLOW_CREATED"))
                .andExpect(jsonPath("$.data.profileType")
                        .value("OTHER_FOLLOWING"));

        mockMvc.perform(post("/api/v2/users/{userId}/followers/me", target.getUserId())
                        .with(csrf())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ALREADY_FOLLOWING"));

        assertThat(userFollowRepository.count()).isOne();
        assertThat(outboxRepository.count()).isOne();

        projectionTransactions.processPending();
        mockMvc.perform(get("/api/v2/users/{userId}/profile", target.getUserId())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileType")
                        .value("OTHER_FOLLOWING"))
                .andExpect(jsonPath("$.data.followerCount").value(1));

        mockMvc.perform(delete("/api/v2/users/{userId}/followers/me", target.getUserId())
                        .with(csrf())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("FOLLOW_DELETED"));
        projectionTransactions.processPending();

        mockMvc.perform(get("/api/v2/users/{userId}/profile", target.getUserId())
                        .cookie(access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profileType")
                        .value("OTHER_NOT_FOLLOWING"))
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    @DisplayName("본인 팔로우는 거절하고 관계나 이벤트를 만들지 않는다")
    void rejectSelfFollow() throws Exception {
        User viewer = saveUser("self-follow@example.com", "selfFollow");

        mockMvc.perform(post("/api/v2/users/{userId}/followers/me", viewer.getUserId())
                        .with(csrf())
                        .cookie(accessCookie(viewer)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_FOLLOW_NOT_ALLOWED"));

        assertThat(userFollowRepository.count()).isZero();
        assertThat(outboxRepository.count()).isZero();
    }

    @Test
    @DisplayName("사용자별 피드는 대상 작성자의 삭제되지 않은 게시글만 Slice로 반환한다")
    void userPosts() throws Exception {
        User viewer = saveUser("posts-viewer@example.com", "postViewer");
        User target = saveUser("posts-target@example.com", "postTarget");
        postRepository.save(new Post(target, "대상 피드"));
        postRepository.save(new Post(viewer, "다른 피드"));

        mockMvc.perform(get("/api/v2/users/{userId}/posts", target.getUserId())
                        .param("page", "0")
                        .param("size", "100")
                        .cookie(accessCookie(viewer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("NO_MORE_POSTS"))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].content")
                        .value("대상 피드"))
                .andExpect(jsonPath("$.data.content[0].author.userId")
                        .value(target.getUserId()));
    }

    private User saveUser(String email, String nickname) {
        return userRepository.saveAndFlush(
                new User(email, "encoded-password", nickname)
        );
    }

    private Cookie accessCookie(User user) {
        return new Cookie(
                JwtCookieProvider.ACCESS_TOKEN_COOKIE,
                tokenService.issueInitialTokens(user).accessToken()
        );
    }
}
