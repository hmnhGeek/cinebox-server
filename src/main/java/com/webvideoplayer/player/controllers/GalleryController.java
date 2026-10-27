
package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.GalleryImagePageDTO;
import com.webvideoplayer.player.services.impl.GalleryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/collections/{collectionId}/videos/{mediaItemId}/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService galleryService;

    /**
     * Returns a paginated list of images in a movie's gallery folder.
     */
    @GetMapping
    public ResponseEntity<GalleryImagePageDTO> getGalleryImages(
            @PathVariable String collectionId,
            @PathVariable String mediaItemId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        GalleryImagePageDTO response = galleryService.getGalleryImages(
                collectionId,
                mediaItemId,
                page,
                size
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Serves an individual gallery image.
     */

    @GetMapping("/{imageId}")
    public ResponseEntity<Resource> getGalleryImage(
            @PathVariable String collectionId,
            @PathVariable String mediaItemId,
            @PathVariable String imageId
    ) {
        GalleryService.GalleryImageFile image =
                galleryService.getGalleryImage(
                        collectionId,
                        mediaItemId,
                        imageId
                );

        return ResponseEntity.ok()
                .contentType(image.contentType())
                .contentLength(image.sizeBytes())
                .cacheControl(
                        CacheControl.maxAge(Duration.ofHours(1))
                                .cachePrivate()
                )
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(
                                        image.filename(),
                                        StandardCharsets.UTF_8
                                )
                                .build()
                                .toString()
                )
                .body(image.resource());
    }
}
