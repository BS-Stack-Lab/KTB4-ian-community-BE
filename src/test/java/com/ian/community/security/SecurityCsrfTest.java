package com.ian.community.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@SpringBootTest(
        properties = {
                "jwt.secret="
                        + "MDEyMzQ1Njc4OWFiY2RlZj"
                        + "AxMjM0NTY3ODlhYmNkZWY="
        }
)
@AutoConfigureMockMvc
class SecurityCsrfTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName(
            "CSRF 발급 API는 XSRF-TOKEN 쿠키를 발급한다"
    )
    void issueCsrfToken() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                get("/api/csrf")
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                cookie().exists(
                                        "XSRF-TOKEN"
                                )
                        )
                        .andReturn();

        Cookie csrfCookie =
                result.getResponse()
                        .getCookie("XSRF-TOKEN");

        assertThat(csrfCookie)
                .isNotNull();

        assertThat(
                csrfCookie.isHttpOnly()
        ).isFalse();

        assertThat(
                csrfCookie.getValue()
        ).isNotBlank();
    }

    @Test
    @DisplayName(
            "H2 Console이 비활성화되면 공개 경로로 허용하지 않는다"
    )
    void disabledH2ConsoleIsNotPublic() throws Exception {
        mockMvc.perform(
                        get("/h2-console/")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    @Test
    @DisplayName(
            "CSRF 토큰 없이 로그인 요청 시 403을 반환한다"
    )
    void loginWithoutCsrfToken()
            throws Exception {

        mockMvc.perform(
                        post("/api/users/login")
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    @DisplayName(
            "정상 CSRF 쿠키와 헤더가 있으면 로그인 Controller까지 전달된다"
    )
    void loginWithValidCsrfToken()
            throws Exception {

        Cookie csrfCookie =
                requestCsrfCookie();

        mockMvc.perform(
                        post("/api/users/login")
                                .cookie(csrfCookie)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCookie
                                                .getValue()
                                )
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isBadRequest()
                );
    }

    @Test
    @DisplayName(
            "CSRF 헤더가 변조되면 로그인 요청은 403을 반환한다"
    )
    void loginWithInvalidCsrfToken()
            throws Exception {

        Cookie csrfCookie =
                requestCsrfCookie();

        mockMvc.perform(
                        post("/api/users/login")
                                .cookie(csrfCookie)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCookie
                                                .getValue()
                                                + "-tampered"
                                )
                                .contentType(
                                        MediaType
                                                .APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    @DisplayName(
            "정상 CSRF 쿠키와 헤더가 있으면 로그아웃에 성공한다"
    )
    void logoutWithValidCsrfToken()
            throws Exception {

        Cookie csrfCookie =
                requestCsrfCookie();

        mockMvc.perform(
                        post("/api/users/logout")
                                .cookie(csrfCookie)
                                .header(
                                        "X-XSRF-TOKEN",
                                        csrfCookie
                                                .getValue()
                                )
                )
                .andExpect(
                        status().isNoContent()
                );
    }

    @Test
    @DisplayName(
            "CSRF 토큰 없이 로그아웃하면 403을 반환한다"
    )
    void logoutWithoutCsrfToken()
            throws Exception {

        mockMvc.perform(
                        post("/api/users/logout")
                )
                .andExpect(
                        status().isForbidden()
                );
    }

    @Test
    @DisplayName(
            "H2 Console이 비활성화된 프로필에서는 인증 없이 접근할 수 없다"
    )
    void h2ConsoleIsNotPublicWhenDisabled()
            throws Exception {

        mockMvc.perform(
                        get("/h2-console")
                )
                .andExpect(
                        status().isUnauthorized()
                );
    }

    private Cookie requestCsrfCookie()
            throws Exception {

        MvcResult result =
                mockMvc.perform(
                                get("/api/csrf")
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                cookie().exists(
                                        "XSRF-TOKEN"
                                )
                        )
                        .andReturn();

        Cookie csrfCookie =
                result.getResponse()
                        .getCookie("XSRF-TOKEN");

        assertThat(csrfCookie)
                .isNotNull();

        return csrfCookie;
    }
}
