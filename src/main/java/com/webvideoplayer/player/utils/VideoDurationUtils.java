package com.webvideoplayer.player.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
                        .redirectErrorStream(false)
                        .redirectError(ProcessBuilder.Redirect.DISCARD)
                        .start();

        String output;

        try {
            output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            ).trim();
        } catch (IOException e) {
            process.destroyForcibly();
            throw e;
        }

        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException(
                    "ffprobe failed for: " + videoPath
            );
        }

        if (output.isEmpty()) {
            throw new IOException(
                    "ffprobe returned an empty duration for: " + videoPath
            );
        }

        try {
            double duration = Double.parseDouble(output);

            if (!Double.isFinite(duration) || duration < 0) {
                throw new IOException(
                        "Invalid video duration returned by ffprobe for: "
                                + videoPath
                );
            }

            return duration;

        } catch (NumberFormatException e) {
            throw new IOException(
                    "Invalid duration returned by ffprobe for: "
                            + videoPath + ". Output: " + output,
                    e
            );
        }
    }
}
