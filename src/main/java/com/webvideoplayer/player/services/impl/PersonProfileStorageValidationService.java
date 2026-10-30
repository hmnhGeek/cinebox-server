
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.config.PersonProfileStorageProperties;
import com.webvideoplayer.player.entities.PersonProfile;
import com.webvideoplayer.player.repositories.PersonProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonProfileStorageValidationService {

    private final PersonProfileRepository personProfileRepository;
    private final PersonProfileStorageProperties storageProperties;

    /**
     * Validates profile image paths without changing database records.
     *
     * If storage is unavailable or its marker is missing, the scan is skipped.
     */
    public void validateStorage() {
        try {
            Path configuredRoot = storageProperties.getRoot();

            if (configuredRoot == null) {
                log.warn(
                        "Person profile storage validation skipped: no storage root configured."
                );
                return;
            }

            Path root = configuredRoot.toAbsolutePath().normalize();

            if (!Files.isDirectory(root)) {
                log.warn(
                        "Person profile storage validation skipped: configured storage root is unavailable."
                );
                return;
            }

            Path realRoot = root.toRealPath();

            if (!isStorageMarkerAvailable(root, realRoot)) {
                log.warn(
                        "Person profile storage validation skipped: storage marker is missing or invalid."
                );
                return;
            }

            List<PersonProfile> profiles = personProfileRepository.findAll();

            int checked = 0;
            int missing = 0;
            int invalid = 0;

            for (PersonProfile profile : profiles) {
                PathCheckResult heroResult =
                        validateImagePath(realRoot, profile.getHeroImagePath());

                PathCheckResult profileResult =
                        validateImagePath(realRoot, profile.getProfileImagePath());

                checked += heroResult.checked() ? 1 : 0;
                checked += profileResult.checked() ? 1 : 0;

                missing += heroResult.missing() ? 1 : 0;
                missing += profileResult.missing() ? 1 : 0;

                invalid += heroResult.invalid() ? 1 : 0;
                invalid += profileResult.invalid() ? 1 : 0;
            }

            log.info(
                    "Person profile storage validation completed. Profiles: {}, image paths checked: {}, missing files: {}, invalid paths: {}.",
                    profiles.size(),
                    checked,
                    missing,
                    invalid
            );

        } catch (IOException | InvalidPathException exception) {
            log.warn(
                    "Person profile storage validation could not complete because storage could not be accessed.",
                    exception
            );
        } catch (Exception exception) {
            log.error(
                    "Unexpected error during person profile storage validation. Database records were not intentionally modified.",
                    exception
            );
        }
    }

    private boolean isStorageMarkerAvailable(Path root, Path realRoot)
            throws IOException {

        String markerName = storageProperties.getMarkerFileName();

        if (markerName == null || markerName.isBlank()) {
            return false;
        }

        Path markerRelativePath = Path.of(markerName);

        if (markerRelativePath.isAbsolute()) {
            return false;
        }

        Path marker = root.resolve(markerRelativePath).normalize();

        if (!marker.startsWith(root)) {
            return false;
        }

        if (!Files.isRegularFile(marker)) {
            return false;
        }

        Path realMarker = marker.toRealPath();

        return realMarker.startsWith(realRoot)
                && Files.isRegularFile(realMarker);
    }

    private PathCheckResult validateImagePath(
            Path realRoot,
            String storedRelativePath) {

        // An unset image is valid; it simply means the profile has no image.
        if (storedRelativePath == null || storedRelativePath.isBlank()) {
            return PathCheckResult.empty();
        }

        try {
            Path relativePath = Path.of(storedRelativePath);

            if (relativePath.isAbsolute()) {
                log.warn(
                        "Rejected absolute person profile image path."
                );
                return PathCheckResult.invalidPath();
            }

            Path resolved = realRoot.resolve(relativePath).normalize();

            if (!resolved.startsWith(realRoot)) {
                log.warn(
                        "Rejected person profile image path that escapes the configured storage root."
                );
                return PathCheckResult.invalidPath();
            }

            if (!Files.exists(resolved)) {
                log.warn(
                        "Person profile image file is missing. Stored path: {}",
                        storedRelativePath
                );
                return PathCheckResult.missingFile();
            }

            // Resolve symlinks as well, so a path cannot escape the root
            // by pointing to a file outside the configured storage folder.
            Path realFile = resolved.toRealPath();

            if (!realFile.startsWith(realRoot)
                    || !Files.isRegularFile(realFile)) {
                log.warn(
                        "Rejected invalid person profile image path: {}",
                        storedRelativePath
                );
                return PathCheckResult.invalidPath();
            }

            return PathCheckResult.validFile();

        } catch (IOException | InvalidPathException exception) {
            log.warn(
                    "Could not validate person profile image path: {}",
                    storedRelativePath
            );
            return PathCheckResult.missingFile();
        }
    }

    private record PathCheckResult(
            boolean checked,
            boolean missing,
            boolean invalid) {

        static PathCheckResult empty() {
            return new PathCheckResult(false, false, false);
        }

        static PathCheckResult validFile() {
            return new PathCheckResult(true, false, false);
        }

        static PathCheckResult missingFile() {
            return new PathCheckResult(true, true, false);
        }

        static PathCheckResult invalidPath() {
            return new PathCheckResult(false, false, true);
        }
    }
}
