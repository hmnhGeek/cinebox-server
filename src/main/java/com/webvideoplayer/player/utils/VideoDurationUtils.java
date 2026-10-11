package com.webvideoplayer.player.utils;

import java.io.IOException;
import java.nio.file.Path;

public final class VideoDurationUtils {

    private VideoDurationUtils() {
    }

    public static double getDuration(
            Path videoPath
    ) throws IOException, InterruptedException {

        Process process =
                new ProcessBuilder(
                        "ffprobe",
                        "-v",
                        "error",
                        "-show_entries",
                        "format=duration",
                        "-of",
                        "default=noprint_wrappers=1:nokey=1",
                        videoPath.toString()
                )
                        .redirectErrorStream(true)
                        .start();

        String output =
                new String(
                        process.getInputStream()
                                .readAllBytes()
                ).trim();

        int exitCode =
                process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                    "ffprobe failed for: " + videoPath
            );
        }

        return Double.parseDouble(output);
    }
}