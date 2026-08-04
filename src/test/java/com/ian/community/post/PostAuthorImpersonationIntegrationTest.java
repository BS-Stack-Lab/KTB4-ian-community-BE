package com.ian.community.post;

import com.ian.community.post.domain.Post;
import com.ian.community.post.domain.PostComment;
import com.ian.community.post.repository.CommentRepository;
import com.ian.community.post.repository.PostRepository;
import com.ian.community.security.jwt.JwtCookieProvider;
import com.ian.community.security.token.TokenService;
import com.ian.community.user.domain.User;
import com.ian.community.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PostAuthorImpersonationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private TokenService tokenService;

    @Test
    @DisplayName("피드 생성 API는 인증 사용자를 작성자로 사용한다")
    void postAuthorComesFromAuthentication() throws Exception {
        User authenticatedUser = saveUser(
                "post-actor@example.com",
                "피드작성자"
        );
        User impersonatedUser = saveUser(
                "post-victim@example.com",
                "피드피해자"
        );
        String content = "사용자 ID 조작 피드";
        MockMultipartFile contentPart = new MockMultipartFile(
                "content",
                "",
                MediaType.TEXT_PLAIN_VALUE,
                content.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/posts/me")
                                .file(contentPart)
                                .with(csrf())
                                .cookie(accessCookie(authenticatedUser))
                )
                .andExpect(status().isCreated());

        Post createdPost = postRepository.findAll()
                .stream()
                .filter(post -> content.equals(post.getContent()))
                .findFirst()
                .orElseThrow();

        assertThat(createdPost.getAuthorUser().getUserId())
                .isEqualTo(authenticatedUser.getUserId())
                .isNotEqualTo(impersonatedUser.getUserId());
    }

    @Test
    @DisplayName("댓글 생성 API는 인증 사용자를 작성자로 사용한다")
    void commentAuthorComesFromAuthentication() throws Exception {
        User authenticatedUser = saveUser(
                "comment-actor@example.com",
                "댓글작성자"
        );
        User impersonatedUser = saveUser(
                "comment-victim@example.com",
                "댓글피해자"
        );
        Post post = postRepository.saveAndFlush(
                new Post(impersonatedUser, "댓글 대상 피드")
        );

        MvcResult result = mockMvc.perform(
                        post(
                                "/api/posts/{postId}/comments/users/me",
                                post.getPostId()
                        )
                                .with(csrf())
                                .cookie(accessCookie(authenticatedUser))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"사용자 ID 조작 댓글\"}")
                )
                .andExpect(status().isCreated())
                .andReturn();

        Long commentId = Long.valueOf(
                result.getResponse().getContentAsString()
        );
        PostComment createdComment = commentRepository.findById(commentId)
                .orElseThrow();

        assertThat(createdComment.getAuthorUser().getUserId())
                .isEqualTo(authenticatedUser.getUserId())
                .isNotEqualTo(impersonatedUser.getUserId());
    }

    private User saveUser(
            String email,
            String nickname
    ) {
        return userRepository.saveAndFlush(
                new User(
                        email,
                        "encoded-password",
                        nickname
                )
        );
    }

    private Cookie accessCookie(User user) {
        return new Cookie(
                JwtCookieProvider.ACCESS_TOKEN_COOKIE,
                tokenService.issueInitialTokens(user).accessToken()
        );
    }
}
