package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.MetadataOperationResponseDTO;
import com.webvideoplayer.player.dto.NfoSyncStatusDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.services.impl.MediaMetadataManagementService;
import com.webvideoplayer.player.services.impl.NfoSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/media-items")
@RequiredArgsConstructor
public class MediaMetadataController {

    private final MediaMetadataManagementService metadataService;
    private final NfoSyncService nfoSyncService;

    /*
     * Returns editable metadata from SQLite.
     */
    @GetMapping("/{mediaItemId}/metadata")
    public VideoMetadata getMetadata(
            @PathVariable String mediaItemId
    ) {

        try {
            return metadataService.getMetadata(mediaItemId);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage(),
                    e
            );
        }
    }

    /*
     * Saves metadata to SQLite first, then synchronizes the NFO file.
     *
     * A failed NFO write does not undo the database update.
     */
    @PutMapping("/{mediaItemId}/metadata")
    public MetadataOperationResponseDTO updateMetadata(
            @PathVariable String mediaItemId,
            @RequestBody VideoMetadata metadata
    ) {

        try {
            VideoMetadata savedMetadata =
                    metadataService.updateMetadata(
                            mediaItemId,
                            metadata
                    );

            NfoSyncStatusDTO nfoStatus =
                    nfoSyncService.synchronize(mediaItemId);

            return MetadataOperationResponseDTO.builder()
                    .metadata(savedMetadata)
                    .nfoStatus(nfoStatus)
                    .build();

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage(),
                    e
            );
        }
    }

    /*
     * Explicitly imports metadata from the existing NFO file.
     *
     * This is the only operation that replaces existing SQLite metadata
     * with the contents of an existing NFO file.
     */
    @PostMapping("/{mediaItemId}/nfo/reimport")
    public MetadataOperationResponseDTO reimportNfo(
            @PathVariable String mediaItemId
    ) {

        try {
            VideoMetadata importedMetadata =
                    metadataService.reimportNfo(mediaItemId);

            NfoSyncStatusDTO nfoStatus =
                    nfoSyncService.getStatus(mediaItemId);

            return MetadataOperationResponseDTO.builder()
                    .metadata(importedMetadata)
                    .nfoStatus(nfoStatus)
                    .build();

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage(),
                    e
            );

        } catch (IOException e) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not import NFO metadata: " + e.getMessage(),
                    e
            );
        }
    }

    /*
     * Retries NFO synchronization or generates an NFO if one is missing.
     */
    @PostMapping("/{mediaItemId}/nfo/sync")
    public NfoSyncStatusDTO synchronizeNfo(
            @PathVariable String mediaItemId
    ) {

        try {
            return nfoSyncService.synchronize(mediaItemId);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage(),
                    e
            );
        }
    }

    /*
     * Returns NFO synchronization status independently of metadata.
     */
    @GetMapping("/{mediaItemId}/nfo/status")
    public NfoSyncStatusDTO getNfoStatus(
            @PathVariable String mediaItemId
    ) {

        try {
            return nfoSyncService.getStatus(mediaItemId);

        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    e.getMessage(),
                    e
            );
        }
    }
}