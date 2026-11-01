package com.webvideoplayer.player.utils;

import com.webvideoplayer.player.entities.MediaItem;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class MediaFilePathResolver {

    public Path resolve(MediaItem mediaItem) throws IOException {

        if (mediaItem == null
                || mediaItem.getCollection() == null
                || mediaItem.getCollection().getFolder() == null
                || mediaItem.getRelativePath() == null) {
            throw new IOException("Media file information is incomplete.");
        }

        Path collectionFolder = Paths.get(
                mediaItem.getCollection().getFolder()
        ).toAbsolutePath().normalize();

        if (!Files.isDirectory(collectionFolder)) {
            throw new IOException("Collection folder does not exist.");
        }

        Path realCollectionFolder = collectionFolder.toRealPath();

        Path videoPath = collectionFolder
                .resolve(mediaItem.getRelativePath())
                .normalize();

        if (!videoPath.startsWith(collectionFolder)) {
            throw new IOException(
                    "Resolved media path is outside the collection folder."
            );
        }

        if (!Files.isRegularFile(videoPath) || !Files.isReadable(videoPath)) {
            throw new IOException("Media file does not exist or is not readable.");
        }

        Path realVideoPath = videoPath.toRealPath();

        if (!realVideoPath.startsWith(realCollectionFolder)) {
            throw new IOException(
                    "Media file resolves outside the collection folder."
            );
        }

        return realVideoPath;
    }
}