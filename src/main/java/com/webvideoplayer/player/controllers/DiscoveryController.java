
package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.ActorBrowseDTO;
import com.webvideoplayer.player.dto.VideoMetadata;
import com.webvideoplayer.player.services.impl.DiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery")
@RequiredArgsConstructor
public class DiscoveryController {

    private final DiscoveryService discoveryService;

    /**
     * Returns a paginated list of distinct actors across available collections.
     *
     * Example:
     * GET /api/v1/discovery/actors?page=0&size=30
     */
    @GetMapping("/actors")
    public Page<ActorBrowseDTO> getAllActors(
            @PageableDefault(size = 30) Pageable pageable
    ) {
        return discoveryService.getAllActors(pageable);
    }

    @GetMapping("/tags/{tagId}/videos")
    public Page<VideoMetadata> getVideosByTag(
            @PathVariable String tagId,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return discoveryService.getVideosByTag(tagId, pageable);
    }

    @GetMapping("/genres/{genreId}/videos")
    public Page<VideoMetadata> getVideosByGenre(
            @PathVariable String genreId,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return discoveryService.getVideosByGenre(genreId, pageable);
    }

    @GetMapping("/actors/{personId}/videos")
    public Page<VideoMetadata> getVideosByActor(
            @PathVariable String personId,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return discoveryService.getVideosByActor(personId, pageable);
    }

    @GetMapping("/directors/{personId}/videos")
    public Page<VideoMetadata> getVideosByDirector(
            @PathVariable String personId,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return discoveryService.getVideosByDirector(personId, pageable);
    }

    @GetMapping("/studios/{studioId}/videos")
    public Page<VideoMetadata> getVideosByStudio(
            @PathVariable String studioId,
            @PageableDefault(size = 24) Pageable pageable
    ) {
        return discoveryService.getVideosByStudio(studioId, pageable);
    }
}
