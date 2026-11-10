package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.PlaybackProgressDTO;
import com.webvideoplayer.player.dto.UpdatePlaybackProgressRequest;
import com.webvideoplayer.player.services.impl.PlaybackProgressService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/playback")
@RequiredArgsConstructor
public class PlaybackProgressController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final PlaybackProgressService playbackProgressService;

    /**
     * Returns paginated Continue Watching items.
     */
    @GetMapping("/continue-watching")
    public ResponseEntity<Page<PlaybackProgressDTO>> getContinueWatching(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {

        Pageable pageable = createPageable(page, size);

        return ResponseEntity.ok(
                playbackProgressService.getContinueWatching(pageable)
        );
    }

    /**
     * Gets saved progress for a media item.
     */
    @GetMapping("/media-items/{mediaItemId}/progress")
    public ResponseEntity<PlaybackProgressDTO> getProgress(
            @PathVariable String mediaItemId
    ) {

        return ResponseEntity.ok(
                playbackProgressService.getProgress(mediaItemId)
        );
    }

    /**
     * Creates or updates playback progress.
     */
    @PutMapping("/media-items/{mediaItemId}/progress")
    public ResponseEntity<PlaybackProgressDTO> updateProgress(
            @PathVariable String mediaItemId,
            @RequestBody UpdatePlaybackProgressRequest request
    ) {

        return ResponseEntity.ok(
                playbackProgressService.updateProgress(
                        mediaItemId,
                        request
                )
        );
    }

    /**
     * Deletes saved progress for a media item.
     */
    @DeleteMapping("/media-items/{mediaItemId}/progress")
    public ResponseEntity<Void> deleteProgress(
            @PathVariable String mediaItemId
    ) {

        playbackProgressService.deleteProgress(mediaItemId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Marks a media item as fully watched.
     */
    @PostMapping("/media-items/{mediaItemId}/complete")
    public ResponseEntity<PlaybackProgressDTO> markCompleted(
            @PathVariable String mediaItemId
    ) {

        return ResponseEntity.ok(
                playbackProgressService.markCompleted(mediaItemId)
        );
    }

    private Pageable createPageable(int page, int size) {

        if (page < 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page index must be zero or greater."
            );
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page size must be between 1 and "
                            + MAX_PAGE_SIZE + "."
            );
        }

        return PageRequest.of(
                page,
                Math.min(size, MAX_PAGE_SIZE),
                Sort.unsorted()
        );
    }
}