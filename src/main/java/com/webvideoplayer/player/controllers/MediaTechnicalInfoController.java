package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.MediaTechnicalInfoDTO;
import com.webvideoplayer.player.services.impl.MediaTechnicalInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/media-items/{mediaItemId}/technical-info")
@RequiredArgsConstructor
public class MediaTechnicalInfoController {

    private final MediaTechnicalInfoService technicalInfoService;

    @GetMapping
    public MediaTechnicalInfoDTO getTechnicalInfo(
            @PathVariable String mediaItemId
    ) {
        return technicalInfoService.getTechnicalInfo(mediaItemId);
    }

    @PostMapping("/sync")
    public MediaTechnicalInfoDTO syncTechnicalInfo(
            @PathVariable String mediaItemId,
            @RequestParam(defaultValue = "false") boolean force
    ) {
        return technicalInfoService.requestExtraction(mediaItemId, force);
    }
}