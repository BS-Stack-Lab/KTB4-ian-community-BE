package com.ian.community.user.controller;

import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.security.principal.AuthenticatedUser;
import com.ian.community.user.service.UserService;
import com.ian.community.user.dto.response.ProfileMediaResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/users")
@RequiredArgsConstructor
public class UserMediaControllerV2 {
    private final UserService userService;

    @PatchMapping("/{userId}/profile-image")
    public ResponseEntity<MediaResponse> update(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId,
            @Valid @RequestBody ProfileMediaRequest request
    ) {
        if (!authenticatedUser.getUserId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(userService.updateProfileMedia(userId, request.mediaId()));
    }

    @GetMapping("/{userId}/profile-image")
    public ResponseEntity<ProfileMediaResponse> get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable Long userId
    ) {
        return ResponseEntity.ok(userService.getProfileMedia(authenticatedUser.getUserId(), userId));
    }

    public record ProfileMediaRequest(@NotNull UUID mediaId) {}
}
