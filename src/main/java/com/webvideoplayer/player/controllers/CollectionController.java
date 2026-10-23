package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.CatalogSyncProgress;
import com.webvideoplayer.player.dto.CreateCollectionRequest;
import com.webvideoplayer.player.dto.RequestThumbnailDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
import com.webvideoplayer.player.enums.CollectionSyncStatus;
import com.webvideoplayer.player.services.impl.CollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    /*
     * 1. Add collection
     */
    @PostMapping
    public ResponseEntity<Collection> addCollection(
            @RequestBody CreateCollectionRequest request
    ) {

        Collection collection =
                collectionService.addCollection(
                        request.getTitle(),
                        request.getFolder(),
                        request.getTagline()
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(collection);
    }

    /*
     * 2. Delete collection
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCollection(
            @PathVariable String id
    ) {

        collectionService.deleteCollection(id);

        return ResponseEntity.noContent().build();
    }

    /*
     * 3. Get all videos in collection
     *
     * Only metadata is returned.
     */
    @GetMapping("/{id}/videos")
    public ResponseEntity<List<VideoMetadata>> getVideos(
            @PathVariable String id
    ) {

        List<VideoMetadata> videos =
                collectionService.getVideos(id);

        return ResponseEntity.ok(videos);
    }

    /*
     * Get all collections
     */
    @GetMapping
    public ResponseEntity<List<Collection>> getAllCollections() {
        List<Collection> collections = collectionService.getAllCollections();

        return ResponseEntity.ok(collections);
    }

    @GetMapping("/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @RequestParam("mediaItemId") String mediaItemId
    ) throws IOException {

        Resource resource = collectionService.getThumbnail(mediaItemId);

        File file = resource.getFile();
        String contentType = Files.probeContentType(file.toPath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType != null
                                ? contentType
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE
                ))
                .body(resource);
    }

    @GetMapping("/preview")
    public ResponseEntity<Resource> getPreview(
            @RequestParam("mediaItemId") String mediaItemId
    ) throws IOException {

        Resource resource = collectionService.getPreview(mediaItemId);

        File file = resource.getFile();
        String contentType = Files.probeContentType(file.toPath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType != null
                                ? contentType
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE
                ))
                .body(resource);
    }

    @GetMapping("/poster")
    public ResponseEntity<Resource> getPoster(
            @RequestParam("mediaItemId") String mediaItemId
    ) throws IOException {

        Resource resource = collectionService.getPoster(mediaItemId);

        File file = resource.getFile();
        String contentType = Files.probeContentType(file.toPath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType != null
                                ? contentType
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE
                ))
                .body(resource);
    }

    @GetMapping("/video")
    public ResponseEntity<Resource> getVideo(
            @RequestParam("mediaItemId") String mediaItemId
    ) throws IOException {

        Resource resource = collectionService.getVideo(mediaItemId);

        File file = resource.getFile();
        String contentType = Files.probeContentType(file.toPath());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        contentType != null
                                ? contentType
                                : MediaType.APPLICATION_OCTET_STREAM_VALUE
                ))
                .body(resource);
    }

    @GetMapping("/metadata")
    public ResponseEntity<VideoMetadata> getVideoMetadata(
            @RequestParam("mediaItemId") String mediaItemId
    ) {
        VideoMetadata videoMetadata =
                collectionService.getVideoMetadata(mediaItemId);

        return ResponseEntity.ok(videoMetadata);
    }

    @PostMapping("/sync")
    public ResponseEntity<Void> sync(@RequestParam String collectionId)
            throws IOException {
        collectionService.synchroniseCollection(collectionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/previews/generate")
    public ResponseEntity<Void> generatePreviews(
            @RequestParam String collectionId) {
        collectionService.generatePreviews(collectionId);
        return ResponseEntity.accepted().build();
    }

//    @GetMapping("/preview/preview-progress")
//    public Map<String, Integer> getPreviewProgress() {
//        CollectionSyncStatus status =
//                collectionService.getSyncStatus(collectionId);
//
//        return Map.of("status", status.name());
//    }

    @GetMapping("/{id}")
    public ResponseEntity<Collection> getCollectionById(@PathVariable String id) {
        Collection collection = collectionService.getCollectionById(id);
        return ResponseEntity.ok(collection);
    }

    @GetMapping("/hero-image")
    public ResponseEntity<Resource> getHeroImage(
            @RequestParam("collectionId") String collectionId
    ) {
        try {
            Resource resource = collectionService.getHeroImage(collectionId);

            // If the file doesn't exist on disk, resource.getFile() throws FileNotFoundException -> 404
            File file = resource.getFile();

            String contentType = Files.probeContentType(file.toPath());

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE
                    ))
                    .body(resource);

        } catch (Exception e) {
            // Print the real error to your IDE console log!
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/sync-status")
    public CatalogSyncProgress getSyncStatus(
            @RequestParam String collectionId
    ) {
        return collectionService.getCatalogSyncProgress(collectionId);
    }
}