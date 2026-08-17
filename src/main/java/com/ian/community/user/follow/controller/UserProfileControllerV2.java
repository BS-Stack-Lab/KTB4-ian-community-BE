package com.ian.community.user.follow.controller;

import com.ian.community.common.ApiResponse;
import com.ian.community.security.principal.AuthenticatedUser;
import com.ian.community.user.follow.dto.FollowStateResponse;
import com.ian.community.user.follow.dto.FollowSuccessCode;
import com.ian.community.user.follow.dto.ProfileSuccessCode;
import com.ian.community.user.follow.dto.UserProfileResponse;
import com.ian.community.user.follow.service.FollowService;
import com.ian.community.user.follow.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserProfileControllerV2 {
    private final UserProfileService userProfileService;
    private final FollowService followService;

    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> profile(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                ProfileSuccessCode.USER_PROFILE_FOUND,
                userProfileService.getProfile(authenticatedUser.getUserId(), userId)
        ));
    }

    @PostMapping("/{userId}/followers/me")
    public ResponseEntity<ApiResponse<FollowStateResponse>> follow(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId
    ) {
        FollowService.FollowResult result = followService.follow(
                authenticatedUser.getUserId(),
                userId
        );
        HttpStatus status = result.code() == FollowSuccessCode.FOLLOW_CREATED
                ? HttpStatus.CREATED
                : HttpStatus.OK;
        return ResponseEntity.status(status)
                .body(ApiResponse.success(result.code(), result.response()));
    }

    @DeleteMapping("/{userId}/followers/me")
    public ResponseEntity<ApiResponse<FollowStateResponse>> unfollow(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId
    ) {
        FollowService.FollowResult result = followService.unfollow(
                authenticatedUser.getUserId(),
                userId
        );
        return ResponseEntity.ok(ApiResponse.success(
                result.code(),
                result.response()
        ));
    }
}
