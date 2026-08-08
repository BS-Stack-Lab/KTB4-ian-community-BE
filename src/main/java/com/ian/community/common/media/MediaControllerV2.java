package com.ian.community.common.media;

import com.ian.community.common.media.dto.MediaResponse;
import com.ian.community.common.media.dto.MediaEditSourceResponse;
import com.ian.community.common.media.dto.MediaRevisionRequest;
import com.ian.community.common.media.dto.MediaRevisionResponse;
import com.ian.community.common.media.dto.MediaUploadRequest;
import com.ian.community.common.media.dto.MediaUploadResponse;
import com.ian.community.security.principal.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v2/media")
@RequiredArgsConstructor
public class MediaControllerV2 {
    private final MediaService mediaService;
    private final MediaRevisionService mediaRevisionService;
    private final MediaRevisionCoordinator mediaRevisionCoordinator;

    @PostMapping("/uploads")
    public ResponseEntity<MediaUploadResponse> initiate(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @Valid @RequestBody MediaUploadRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mediaService.initiateUpload(authenticatedUser.getUserId(), request));
    }

    @PostMapping("/{mediaId}/complete")
    public ResponseEntity<MediaResponse> complete(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId
    ) {
        return ResponseEntity.accepted()
                .body(mediaService.completeUpload(authenticatedUser.getUserId(), mediaId));
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<MediaResponse> get(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId
    ) {
        return ResponseEntity.ok(mediaService.getOwned(authenticatedUser.getUserId(), mediaId));
    }

    @DeleteMapping("/{mediaId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId
    ) {
        mediaService.cancel(authenticatedUser.getUserId(), mediaId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{mediaId}/edit-source")
    public ResponseEntity<MediaEditSourceResponse> editSource(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId
    ) {
        return ResponseEntity.ok(mediaRevisionService.editSource(
                authenticatedUser.getUserId(), mediaId
        ));
    }

    @PostMapping("/{mediaId}/revisions")
    public ResponseEntity<MediaRevisionResponse> createRevision(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId,
            @Valid @RequestBody MediaRevisionRequest request
    ) {
        return ResponseEntity.accepted().body(mediaRevisionCoordinator.create(
                authenticatedUser.getUserId(), mediaId, request
        ));
    }

    @GetMapping("/{mediaId}/revisions/{revision}")
    public ResponseEntity<MediaRevisionResponse> getRevision(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId,
            @PathVariable int revision
    ) {
        return ResponseEntity.ok(mediaRevisionService.get(
                authenticatedUser.getUserId(), mediaId, revision
        ));
    }

    @DeleteMapping("/{mediaId}/revisions/{revision}")
    public ResponseEntity<Void> cancelRevision(
            @AuthenticationPrincipal AuthenticatedUser authenticatedUser,
            @PathVariable UUID mediaId,
            @PathVariable int revision
    ) {
        mediaRevisionService.cancel(authenticatedUser.getUserId(), mediaId, revision);
        return ResponseEntity.noContent().build();
    }
}
