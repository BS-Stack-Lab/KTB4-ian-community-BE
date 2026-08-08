package com.ian.community.user.service;

import com.ian.community.common.exception.CustomException;
import com.ian.community.common.exception.ErrorCode;
import com.ian.community.common.media.MediaAsset;
import com.ian.community.common.media.MediaPurpose;
import com.ian.community.common.media.MediaService;
import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.user.domain.User;
import com.ian.community.user.dto.request.*;
import com.ian.community.user.dto.response.UserResponse;
import com.ian.community.user.dto.response.ProfileMediaResponse;
import com.ian.community.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MediaService mediaService;

    private User getActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isUserDeleted()) {
            throw new CustomException(ErrorCode.USER_ALREADY_DELETED);
        }

        return user;
    }

    @Transactional
    public User signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new CustomException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        if (!Objects.equals(request.getPassword(), request.getPasswordConfirm())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD);
        }

        String encodedPassword =
                passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getEmail(),
                encodedPassword,
                request.getNickname()
        );

        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        User user = getActiveUser(userId);

        return new UserResponse(
                user.getUserId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImage()
        );
    }

    @Transactional(readOnly = true)
    public UserResponse getUserForAuthenticatedUser(
            Long authenticatedUserId,
            Long requestedUserId
    ) {
        if (!Objects.equals(
                authenticatedUserId,
                requestedUserId
        )) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }

        return getCurrentUser(authenticatedUserId);
    }

    @Transactional(readOnly = true)
    public User login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new CustomException(
                                ErrorCode.INVALID_LOGIN_REQUEST
                        )
                );

        if (user.isUserDeleted()) {
            throw new CustomException(
                    ErrorCode.INVALID_LOGIN_REQUEST
            );
        }

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new CustomException(
                    ErrorCode.INVALID_LOGIN_REQUEST
            );
        }

        return user;
    }

    @Transactional
    public void updateNickname(Long userId, UserNicknameUpdateRequest request) {
        User user = getActiveUser(userId);

        if (user.getNickname()
                .equals(request.getNickname())) {
            throw new CustomException(ErrorCode.NO_CHANGES_DETECTED);
        }

        if (userRepository.existsByNickname(request.getNickname())) {
            throw new CustomException(ErrorCode.NICKNAME_ALREADY_EXISTS);
        }

        user.updateNickname(request.getNickname());
    }

    @Transactional
    public void updateProfile(Long userId, String profileImage) {

        User user = getActiveUser(userId);
        MediaAsset previousMedia = user.getProfileMedia();

        if (user.getProfileImage()
                .equals(profileImage)) {
            throw new CustomException(ErrorCode.NO_CHANGES_DETECTED);
        }

        user.updateProfile(profileImage);
        userRepository.flush();
        if (previousMedia != null) {
            mediaService.deleteIfUnreferenced(userId, previousMedia.getMediaId());
        }
    }

    @Transactional
    public MediaResponse updateProfileMedia(Long userId, UUID mediaId) {
        User user = getActiveUser(userId);
        MediaAsset previousMedia = user.getProfileMedia();
        MediaAsset media = mediaService.requireReadyMedia(
                userId,
                MediaPurpose.PROFILE,
                java.util.List.of(mediaId)
        ).getFirst();
        user.updateProfileMedia(media, mediaService.compatibilityUrl(media));
        userRepository.flush();
        if (previousMedia != null && !previousMedia.getMediaId().equals(mediaId)) {
            mediaService.deleteIfUnreferenced(userId, previousMedia.getMediaId());
        }
        return mediaService.toResponse(media);
    }

    @Transactional(readOnly = true)
    public ProfileMediaResponse getProfileMedia(Long authenticatedUserId, Long requestedUserId) {
        if (!Objects.equals(authenticatedUserId, requestedUserId)) {
            throw new CustomException(ErrorCode.FORBIDDEN);
        }
        User user = getActiveUser(requestedUserId);
        MediaResponse profileMedia = user.getProfileMedia() == null
                ? null
                : mediaService.toResponse(user.getProfileMedia());
        return new ProfileMediaResponse(
                profileMedia,
                profileMedia == null ? user.getProfileImage() : null
        );
    }

    @Transactional
    public void updatePassword(
            Long userId,
            UserPasswordUpdateRequest request) {

        User user = getActiveUser(userId);

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new CustomException(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        }

        if (!Objects.equals(
                request.getNewPassword(),
                request.getNewPasswordConfirm()
        )) {
            throw new CustomException(ErrorCode.NEW_PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(
                request.getNewPassword(),
                user.getPassword()
        )) {
            throw new CustomException(ErrorCode.NO_CHANGES_DETECTED);
        }

        String encodedPassword =
                passwordEncoder.encode(request.getNewPassword());

        user.updatePassword(encodedPassword);
    }

    @Transactional
    public void deleteUser(Long userId) {
        User user = getActiveUser(userId);

        user.delete();
    }
}
