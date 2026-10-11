package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.entities.Video;
import com.webvideoplayer.player.services.VideoService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/api/v1/videos")
public class VideoController {
    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/upload")
    public ResponseEntity<Video> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam("poster") MultipartFile poster,
            @RequestParam("thumbnail") MultipartFile thumbnail,
            @RequestParam("title") String title,
            @RequestParam("description") String description
    ) throws IOException, InterruptedException {
        Video video = videoService.uploadVideo(
                file,
                poster,
                thumbnail,
                title,
                description
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(video);
    }

    @GetMapping
    public ResponseEntity<List<Video>> getAllVideos() {
        List<Video> allVideos = this.videoService.getAllVideos();
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(allVideos);
    }

    @GetMapping("paginated")
    public ResponseEntity<Page<Video>> getVideos(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(
                videoService.getVideos(page, size)
        );
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<Resource> getThumbnail(
            @PathVariable String id
    ) throws IOException {
        Resource resource = this.videoService.getResource(id, Boolean.TRUE);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @GetMapping("/{id}/poster")
    public ResponseEntity<Resource> getPoster(
            @PathVariable String id
    ) throws IOException {
        Resource resource = this.videoService.getResource(id, Boolean.FALSE);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @GetMapping("/{id}/preview")
    public ResponseEntity<Resource> getVideoPreview(@PathVariable String id) throws IOException {

        Optional<Video> videoOptional = videoService.getVideoById(id);

        if (videoOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = videoOptional.get();

        Path previewPath = Paths.get(video.getPreviewPath());

        if (!Files.exists(previewPath)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(previewPath);

        MediaType mediaType = MediaTypeFactory
                .getMediaType(previewPath.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                .body(resource);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Video> getVideoById(@PathVariable String id) {
        Optional<Video> videoOptional = videoService.getVideoById(id);
        return videoOptional.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<ResourceRegion> streamVideo(
            @PathVariable String id,
            @RequestHeader(value = "Range", required = false) String rangeHeader
    ) throws IOException {

        Optional<Video> videoOptional = videoService.getVideoById(id);

        if (videoOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Video video = videoOptional.get();

        Path videoPath = Paths.get(video.getFilePath());

        if (!Files.exists(videoPath)) {
            return ResponseEntity.notFound().build();
        }

        FileSystemResource resource = new FileSystemResource(videoPath);

        long contentLength = resource.contentLength();

        MediaType mediaType = MediaTypeFactory
                .getMediaType(videoPath.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        // Browser did not send a Range header
        if (rangeHeader == null) {

            ResourceRegion region = new ResourceRegion(
                    resource,
                    0,
                    contentLength
            );

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header("Accept-Ranges", "bytes")
                    .body(region);
        }

        // Browser sent a Range header
        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);

        HttpRange range = ranges.getFirst();

        long start = range.getRangeStart(contentLength);
        long end = range.getRangeEnd(contentLength);

        long rangeLength = end - start + 1;

        ResourceRegion region = new ResourceRegion(
                resource,
                start,
                rangeLength
        );

        return ResponseEntity
                .status(HttpStatus.PARTIAL_CONTENT)
                .contentType(mediaType)
                .header("Accept-Ranges", "bytes")
                .header(
                        "Content-Range",
                        "bytes " + start + "-" + end + "/" + contentLength
                )
                .body(region);
    }
}
