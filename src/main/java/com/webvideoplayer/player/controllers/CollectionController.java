package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.CreateCollectionRequest;
import com.webvideoplayer.player.dto.RequestThumbnailDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.entities.Collection;
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
                        request.getFolder()
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
            @RequestParam("collectionId") String collectionId,
            @RequestParam("videoFolder") String videoFolder
    ) {
        try {
            Resource resource = collectionService.getThumbnail(collectionId, videoFolder);

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

    @GetMapping("/preview")
    public ResponseEntity<Resource> getPreview(
            @RequestParam("collectionId") String collectionId,
            @RequestParam("videoFolder") String videoFolder
    ) {
        try {
            Resource resource = collectionService.getPreview(collectionId, videoFolder);

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

    @GetMapping("/poster")
    public ResponseEntity<Resource> getPoster(
            @RequestParam("collectionId") String collectionId,
            @RequestParam("videoFolder") String videoFolder
    ) {
        try {
            Resource resource = collectionService.getPoster(collectionId, videoFolder);

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

    @GetMapping("/video")
    public ResponseEntity<Resource> getVideo(
            @RequestParam("collectionId") String collectionId,
            @RequestParam("videoFolder") String videoFolder
    ) {
        try {
            Resource resource = collectionService.getVideo(collectionId, videoFolder);

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

    @GetMapping("/metadata")
    public ResponseEntity<VideoMetadata> getVideoMetadata(
            @RequestParam("collectionId") String collectionId,
            @RequestParam("videoFolder") String videoFolder
    ) {
        try {
            VideoMetadata videoMetadata = collectionService.getVideoMetaData(collectionId, videoFolder);
            return ResponseEntity.ok(videoMetadata);
        } catch (Exception e) {
            // Print the real error to your IDE console log!
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/sync")
    public void sync(@RequestParam String collectionId) {
        collectionService.synchroniseCollection(collectionId);
    }
}