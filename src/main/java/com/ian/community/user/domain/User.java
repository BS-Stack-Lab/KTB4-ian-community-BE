package com.ian.community.user.domain;

import com.ian.community.common.media.MediaAsset;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "users")
public class User {
    private static final String DEFAULT_PROFILE_IMAGE = "/images/profile-default.svg";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(length = 256)
    private String email;

    @Column(length = 100)
    private String password;

    @Column(length = 10, nullable = false)
    private String nickname;

    @Column(name = "profile_image", length = 500, nullable = false)
    private String profileImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_media_id")
    private MediaAsset profileMedia;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "password_updated_at")
    private LocalDateTime passwordUpdatedAt;

    @Column(name = "nickname_updated_at")
    private LocalDateTime nicknameUpdatedAt;

    @Column(name = "profile_updated_at")
    private LocalDateTime profileUpdatedAt;

    @Column(name = "user_deleted", nullable = false)
    private boolean userDeleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.profileImage = DEFAULT_PROFILE_IMAGE;
        this.createdAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
        this.passwordUpdatedAt = null;
        this.nicknameUpdatedAt = null;
        this.profileUpdatedAt = null;
        this.userDeleted = false;
        this.deletedAt = null;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameUpdatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void updatePassword(String password) {
        this.password = password;
        this.passwordUpdatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void updateProfile(String profileImage) {
        this.profileImage = profileImage;
        this.profileMedia = null;
        this.profileUpdatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void updateProfileMedia(MediaAsset profileMedia, String compatibilityUrl) {
        this.profileMedia = profileMedia;
        this.profileImage = compatibilityUrl;
        this.profileUpdatedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }

    public void delete() {
        this.email =  null;
        this.password = null;
        this.nickname = "알 수 없음";
        this.profileImage = DEFAULT_PROFILE_IMAGE;
        this.profileMedia = null;
        this.userDeleted = true;
        this.deletedAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
