package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.MediaBarItemDTO;
import com.webvideoplayer.player.services.impl.MediaBarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/media-bar")
@RequiredArgsConstructor
public class MediaBarController {

    private final MediaBarService mediaBarService;

    /**
     * Returns up to five featured movies for the homepage Media Bar.
     */
    @GetMapping
    public ResponseEntity<List<MediaBarItemDTO>> getFeaturedItems() {

        return ResponseEntity.ok(
                mediaBarService.getFeaturedItems()
        );
    }
}