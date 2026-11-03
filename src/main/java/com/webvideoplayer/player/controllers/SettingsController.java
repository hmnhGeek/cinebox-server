package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.ApplicationSettingsDTO;
import com.webvideoplayer.player.dto.UpdateSettingsRequest;
import com.webvideoplayer.player.services.impl.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping
    public ResponseEntity<ApplicationSettingsDTO> getSettings() {

        return ResponseEntity.ok(
                settingsService.getSettings()
        );
    }

    @PutMapping
    public ResponseEntity<ApplicationSettingsDTO> updateSettings(
            @RequestBody UpdateSettingsRequest request
    ) {

        return ResponseEntity.ok(
                settingsService.updateSettings(request)
        );
    }
}