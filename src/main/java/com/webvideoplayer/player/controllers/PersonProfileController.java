
package com.webvideoplayer.player.controllers;

import com.webvideoplayer.player.dto.PersonProfileDTO;
import com.webvideoplayer.player.dto.UpdatePersonProfileRequest;
import com.webvideoplayer.player.services.impl.PersonProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

@RestController
@RequestMapping("/api/v1/people/{personId}/profile")
@RequiredArgsConstructor
public class PersonProfileController {

    private final PersonProfileService personProfileService;

    @GetMapping
    public PersonProfileDTO getProfile(@PathVariable String personId) {
        return personProfileService.getProfile(personId);
    }

    @PutMapping
    public PersonProfileDTO updateProfile(
            @PathVariable String personId,
            @RequestBody UpdatePersonProfileRequest request) {
        return personProfileService.updateProfile(personId, request);
    }

    @PutMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonProfileDTO uploadProfileImage(
            @PathVariable String personId,
            @RequestParam("file") MultipartFile file) {
        return personProfileService.uploadImage(personId, "profile", file);
    }

    @PutMapping(value = "/hero-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonProfileDTO uploadHeroImage(
            @PathVariable String personId,
            @RequestParam("file") MultipartFile file) {
        return personProfileService.uploadImage(personId, "hero", file);
    }

    @GetMapping("/image")
    public ResponseEntity<Resource> getProfileImage(
            @PathVariable String personId) {
        return imageResponse(personProfileService.getImagePath(personId, "profile"));
    }

    @GetMapping("/hero-image")
    public ResponseEntity<Resource> getHeroImage(
            @PathVariable String personId) {
        return imageResponse(personProfileService.getImagePath(personId, "hero"));
    }

    private ResponseEntity<Resource> imageResponse(Path path) {
        String filename = path.getFileName().toString().toLowerCase();

        MediaType mediaType;
        if (filename.endsWith(".png")) {
            mediaType = MediaType.IMAGE_PNG;
        } else if (filename.endsWith(".gif")) {
            mediaType = MediaType.IMAGE_GIF;
        } else {
            mediaType = MediaType.IMAGE_JPEG;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
