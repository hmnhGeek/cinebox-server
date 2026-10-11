package com.webvideoplayer.player.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public final class VideoPreviewUtils {

    private VideoPreviewUtils() {
    }

    /**
     * Generates a muted preview video consisting of multiple short sections
     * distributed throughout the original video.
     *
     * @param inputVideoPath original video
     * @param previewDirectory directory where preview should be stored
     * @param durationSeconds duration of original video
     * @return path to generated preview
     */
    public static Path generatePreview(
            Path inputVideoPath,
            Path previewDirectory,
            double durationSeconds
    ) throws IOException, InterruptedException {

        log.info("Creating preview for {}", inputVideoPath);

        Files.createDirectories(previewDirectory);

        Path previewPath =
                previewDirectory.resolve("preview.mp4");

        /*
         * IMPORTANT:
         * Do not regenerate an existing preview.
         */
        if (Files.exists(previewPath)) {
            log.info(
                    "Preview already exists. Skipping: {}",
                    previewPath
            );

            return previewPath;
        }

        int segmentCount =
                Math.min(
                        8,
                        Math.max(
                                3,
                                (int) (durationSeconds / 300)
                        )
                );

        int segmentDuration = 3;

        List<Double> timestamps =
                calculateTimestamps(
                        durationSeconds,
                        segmentCount,
                        segmentDuration
                );

        List<String> command =
                new ArrayList<>();

        command.add("ffmpeg");
        command.add("-y");
        command.add("-i");
        command.add(inputVideoPath.toString());

        StringBuilder filter =
                new StringBuilder();

        for (int i = 0;
             i < timestamps.size();
             i++) {

            double start = timestamps.get(i);

            filter.append(
                    String.format(
                            "[0:v]trim=start=%.2f:duration=%d,"
                                    + "setpts=PTS-STARTPTS,"
                                    + "scale=640:-2[v%d];",
                            start,
                            segmentDuration,
                            i
                    )
            );
        }

        for (int i = 0;
             i < timestamps.size();
             i++) {

            filter.append("[v").append(i).append("]");
        }

        filter.append(
                String.format(
                        "concat=n=%d:v=1:a=0[outv]",
                        timestamps.size()
                )
        );

        command.add("-filter_complex");
        command.add(filter.toString());

        command.add("-map");
        command.add("[outv]");

        command.add("-an");

        command.add("-c:v");
        command.add("libx264");

        command.add("-preset");
        command.add("veryfast");

        command.add("-crf");
        command.add("28");

        command.add("-pix_fmt");
        command.add("yuv420p");

        command.add("-movflags");
        command.add("+faststart");

        command.add(previewPath.toString());

        Process process =
                new ProcessBuilder(command)
                        .redirectErrorStream(true)
                        .start();

        String output =
                new String(
                        process.getInputStream()
                                .readAllBytes()
                );

        int exitCode =
                process.waitFor();

        if (exitCode != 0) {

            throw new IOException(
                    "FFmpeg failed to generate preview. Output:\n"
                            + output
            );
        }

        return previewPath;
    }

    private static List<Double> calculateTimestamps(
            double duration,
            int segmentCount,
            int segmentDuration
    ) {
        List<Double> timestamps = new ArrayList<>();

        /*
         * Leave a small margin at the beginning and end.
         */
        double startMargin = Math.min(10, duration * 0.02);
        double endMargin = Math.min(10, duration * 0.02);

        double usableDuration =
                duration - startMargin - endMargin - segmentDuration;

        if (usableDuration <= 0) {
            timestamps.add(0.0);
            return timestamps;
        }

        for (int i = 0; i < segmentCount; i++) {

            double position =
                    startMargin +
                            (usableDuration * i / (segmentCount - 1));

            timestamps.add(position);
        }

        return timestamps;
    }

    private static String getBaseName(Path path) {
        String filename = path.getFileName().toString();
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex);
        }
        return filename;
    }
}