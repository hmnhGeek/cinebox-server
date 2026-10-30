
package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.config.PersonProfileStorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class PersonProfileStorageService {

    private static final long MAX_IMAGE_BYTES = 15L * 1024 * 1024;
    private static final int MAX_IMAGE_DIMENSION = 20_000;

    private static final Pattern PERSON_ID_PATTERN =
            Pattern.compile("[a-zA-Z0-9-]{1,100}");

    private final PersonProfileStorageProperties properties;

    public String store(
            String personId,
            String imageType,
            MultipartFile file) {

        validatePersonId(personId);
        validateImageType(imageType);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "An image file is required");
        }

        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "Images must be 15 MB or smaller");
        }

        try {
            byte[] bytes = file.getBytes();

            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                throw new ResponseStatusException(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "Images must be between 1 byte and 15 MB");
            }

            String extension = detectImageExtension(bytes);
            Path root = getValidatedRoot();

            Path actorDirectory = root.resolve(personId).normalize();

            if (!actorDirectory.startsWith(root)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid actor directory");
            }

            // Never follow a pre-existing symbolic link for an actor folder.
            if (Files.exists(
                    actorDirectory,
                    LinkOption.NOFOLLOW_LINKS)) {

                if (Files.isSymbolicLink(actorDirectory)
                        || !Files.isDirectory(
                        actorDirectory,
                        LinkOption.NOFOLLOW_LINKS)) {
                    throw new ResponseStatusException(
                            HttpStatus.SERVICE_UNAVAILABLE,
                            "Actor profile directory is invalid");
                }
            } else {
                Files.createDirectory(actorDirectory);
            }

            Path realActorDirectory = actorDirectory.toRealPath();

            if (!realActorDirectory.startsWith(root)
                    || !realActorDirectory.getParent().equals(root)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Actor profile directory is outside the storage root");
            }

            String filename = "profile".equals(imageType)
                    ? "profile." + extension
                    : "hero." + extension;

            Path destination =
                    realActorDirectory.resolve(filename).normalize();

            if (!destination.startsWith(realActorDirectory)) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid image destination");
            }

            /*
             * CREATE + TRUNCATE_EXISTING allows replacing the selected image.
             * The other image type and all other actors' files are untouched.
             */
            Files.write(
                    destination,
                    bytes,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            // Example: actor-uuid-1/profile.jpg
            return personId + "/" + filename;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InvalidPathException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile image storage is unavailable");
        }
    }

    public Path resolve(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Profile image not found");
        }

        try {
            Path relative = Path.of(storedPath);

            if (relative.isAbsolute()
                    || relative.getNameCount() != 2) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            String personId = relative.getName(0).toString();
            String filename = relative.getName(1).toString();

            validatePersonId(personId);

            if (!filename.matches(
                    "(profile|hero)\\.(jpg|png|gif|webp)")) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            Path root = getValidatedRoot();
            Path actorDirectory = root.resolve(personId).normalize();

            if (!actorDirectory.startsWith(root)
                    || !actorDirectory.getParent().equals(root)
                    || Files.isSymbolicLink(actorDirectory)
                    || !Files.isDirectory(
                    actorDirectory,
                    LinkOption.NOFOLLOW_LINKS)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            Path realActorDirectory = actorDirectory.toRealPath();

            if (!realActorDirectory.startsWith(root)
                    || !realActorDirectory.getParent().equals(root)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            Path image = realActorDirectory.resolve(filename).normalize();

            if (!image.startsWith(realActorDirectory)
                    || !Files.isRegularFile(image)
                    || !Files.isReadable(image)
                    || Files.isSymbolicLink(image)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            Path realImage = image.toRealPath();

            if (!realImage.startsWith(realActorDirectory)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found");
            }

            return realImage;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException | InvalidPathException ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile image storage is unavailable");
        }
    }

    private Path getValidatedRoot() throws IOException {
        Path configuredRoot = properties.getRoot();

        if (configuredRoot == null) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile image storage is not configured");
        }

        Path root = configuredRoot.toAbsolutePath().normalize();

        if (!Files.isDirectory(root) || !Files.isReadable(root)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile image storage is unavailable");
        }

        Path realRoot = root.toRealPath();

        String markerName = properties.getMarkerFileName();

        if (markerName == null || markerName.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile storage marker is not configured");
        }

        Path markerRelative = Path.of(markerName);

        if (markerRelative.isAbsolute()
                || markerRelative.getNameCount() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile storage marker configuration is invalid");
        }

        Path marker = realRoot.resolve(markerRelative).normalize();

        if (!marker.startsWith(realRoot)
                || Files.isSymbolicLink(marker)
                || !Files.isRegularFile(marker)
                || !Files.isReadable(marker)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile storage marker is missing or unavailable");
        }

        Path realMarker = marker.toRealPath();

        if (!realMarker.startsWith(realRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Profile storage marker is outside the storage root");
        }

        return realRoot;
    }

    private void validatePersonId(String personId) {
        if (personId == null
                || !PERSON_ID_PATTERN.matcher(personId).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid person ID");
        }
    }

    private void validateImageType(String imageType) {
        if (!"profile".equals(imageType) && !"hero".equals(imageType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported profile image type");
        }
    }

    private String detectImageExtension(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(
                    new ByteArrayInputStream(bytes));

            if (image == null
                    || image.getWidth() <= 0
                    || image.getHeight() <= 0
                    || image.getWidth() > MAX_IMAGE_DIMENSION
                    || image.getHeight() > MAX_IMAGE_DIMENSION) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Upload a valid JPEG, PNG, GIF, or WebP image");
            }

            // JPEG
            if (matches(bytes, new byte[]{
                    (byte) 0xFF, (byte) 0xD8, (byte) 0xFF
            })) {
                return "jpg";
            }

            // PNG
            if (matches(bytes, new byte[]{
                    (byte) 0x89, 0x50, 0x4E, 0x47,
                    0x0D, 0x0A, 0x1A, 0x0A
            })) {
                return "png";
            }

            // GIF
            if (bytes.length >= 6) {
                String signature = new String(
                        bytes,
                        0,
                        6,
                        StandardCharsets.US_ASCII);

                if ("GIF87a".equals(signature)
                        || "GIF89a".equals(signature)) {
                    return "gif";
                }
            }

            // WebP: RIFF container followed by WEBP signature
            if (bytes.length >= 12
                    && bytes[0] == 'R'
                    && bytes[1] == 'I'
                    && bytes[2] == 'F'
                    && bytes[3] == 'F'
                    && bytes[8] == 'W'
                    && bytes[9] == 'E'
                    && bytes[10] == 'B'
                    && bytes[11] == 'P') {
                return "webp";
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Only JPEG, PNG, GIF, and WebP images are supported");

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unable to read uploaded image");
        }
    }

    private boolean matches(byte[] bytes, byte[] signature) {
        if (bytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (bytes[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }
}
