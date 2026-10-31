package com.webvideoplayer.player.services.impl;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class FFprobeService {

    private static final long PROCESS_TIMEOUT_SECONDS = 60;

    private final ObjectMapper objectMapper;

    public FFprobeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FFprobeResult extract(Path videoPath)
            throws IOException, InterruptedException {

        List<String> command = new ArrayList<>();

        command.add("ffprobe");
        command.add("-v");
        command.add("error");
        command.add("-show_entries");

        command.add(
                "format=duration:" +
                        "stream=index,codec_type,codec_name,width,height," +
                        "duration,avg_frame_rate,r_frame_rate,bit_rate," +
                        "channels,channel_layout:stream_disposition=default"
        );

        command.add("-of");
        command.add("json");
        command.add(videoPath.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(false)
                .start();

        CompletableFuture<byte[]> stdoutFuture =
                CompletableFuture.supplyAsync(() -> readStream(process.getInputStream()));

        CompletableFuture<byte[]> stderrFuture =
                CompletableFuture.supplyAsync(() -> readStream(process.getErrorStream()));

        boolean completed;

        try {
            completed = process.waitFor(
                    PROCESS_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw exception;
        }

        if (!completed) {
            process.destroyForcibly();
            throw new IOException("FFprobe timed out while reading the media file.");
        }

        byte[] stdout = awaitStream(stdoutFuture);
        byte[] stderr = awaitStream(stderrFuture);

        String output = new String(stdout, StandardCharsets.UTF_8);
        String errorOutput = new String(stderr, StandardCharsets.UTF_8).trim();

        if (process.exitValue() != 0) {
            throw new IOException(
                    errorOutput.isBlank()
                            ? "FFprobe could not read the media file."
                            : limit(errorOutput, 900)
            );
        }

        JsonNode root;

        try {
            root = objectMapper.readTree(output);
        } catch (Exception exception) {
            throw new IOException("FFprobe returned invalid metadata.", exception);
        }

        JsonNode streams = root.path("streams");

        JsonNode videoStream = selectStream(streams, "video");
        JsonNode audioStream = selectStream(streams, "audio");

        if (videoStream == null) {
            throw new IOException("No video stream was found in the media file.");
        }

        Integer width = integerValue(videoStream.path("width"));
        Integer height = integerValue(videoStream.path("height"));

        Double frameRate = parseFrameRate(
                firstAvailable(
                        videoStream.path("avg_frame_rate"),
                        videoStream.path("r_frame_rate")
                )
        );

        Long durationSeconds = parseDuration(
                firstAvailable(
                        root.path("format").path("duration"),
                        videoStream.path("duration")
                )
        );

        return new FFprobeResult(
                resolutionLabel(height),
                width,
                height,
                durationSeconds,
                textValue(videoStream.path("codec_name")),
                frameRate,
                longValue(videoStream.path("bit_rate")),
                audioStream == null
                        ? null
                        : textValue(audioStream.path("codec_name")),
                audioStream == null
                        ? null
                        : integerValue(audioStream.path("channels")),
                audioStream == null
                        ? null
                        : textValue(audioStream.path("channel_layout"))
        );
    }

    private JsonNode selectStream(JsonNode streams, String codecType) {

        if (streams == null || !streams.isArray()) {
            return null;
        }

        JsonNode firstMatch = null;

        for (JsonNode stream : streams) {

            if (!codecType.equals(textValue(stream.path("codec_type")))) {
                continue;
            }

            if (firstMatch == null) {
                firstMatch = stream;
            }

            if (integerValue(stream.path("disposition").path("default")) != null
                    && integerValue(
                    stream.path("disposition").path("default")
            ) == 1) {
                return stream;
            }
        }

        return firstMatch;
    }

    private JsonNode firstAvailable(JsonNode first, JsonNode second) {

        if (first != null && !first.isMissingNode()
                && !first.isNull()
                && !first.asText().isBlank()
                && !"N/A".equalsIgnoreCase(first.asText())) {
            return first;
        }

        return second;
    }

    private String resolutionLabel(Integer height) {

        if (height == null || height <= 0) {
            return null;
        }

        if (height >= 2000) {
            return "2160p";
        }

        if (height >= 1300) {
            return "1440p";
        }

        if (height >= 1000) {
            return "1080p";
        }

        if (height >= 700) {
            return "720p";
        }

        if (height >= 550) {
            return "576p";
        }

        if (height >= 400) {
            return "480p";
        }

        return height + "p";
    }

    private Double parseFrameRate(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();

        if (value.isBlank() || "N/A".equalsIgnoreCase(value)) {
            return null;
        }

        try {
            if (value.contains("/")) {
                String[] parts = value.split("/", 2);

                double numerator = Double.parseDouble(parts[0]);
                double denominator = Double.parseDouble(parts[1]);

                if (denominator == 0) {
                    return null;
                }

                return round(numerator / denominator, 3);
            }

            return round(Double.parseDouble(value), 3);

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseDuration(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        try {
            double duration = Double.parseDouble(node.asText());

            if (!Double.isFinite(duration) || duration < 0) {
                return null;
            }

            return Math.round(duration);

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Integer integerValue(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        try {
            String value = node.asText();

            if (value.isBlank() || "N/A".equalsIgnoreCase(value)) {
                return null;
            }

            return Integer.parseInt(value);

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long longValue(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        try {
            String value = node.asText();

            if (value.isBlank() || "N/A".equalsIgnoreCase(value)) {
                return null;
            }

            return Long.parseLong(value);

        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String textValue(JsonNode node) {

        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        String value = node.asText();

        if (value.isBlank() || "N/A".equalsIgnoreCase(value)) {
            return null;
        }

        return value;
    }

    private Double round(double value, int decimalPlaces) {
        double multiplier = Math.pow(10, decimalPlaces);
        return Math.round(value * multiplier) / multiplier;
    }

    private byte[] readStream(java.io.InputStream inputStream) {

        try (inputStream) {
            return inputStream.readAllBytes();
        } catch (IOException exception) {
            throw new RuntimeException("Unable to read FFprobe output.", exception);
        }
    }

    private byte[] awaitStream(CompletableFuture<byte[]> future)
            throws IOException, InterruptedException {

        try {
            return future.get(10, TimeUnit.SECONDS);

        } catch (ExecutionException | TimeoutException exception) {
            throw new IOException("Unable to retrieve FFprobe output.", exception);
        }
    }

    private String limit(String value, int maxLength) {
        return value.length() <= maxLength
                ? value
                : value.substring(0, maxLength);
    }

    public record FFprobeResult(
            String resolution,
            Integer width,
            Integer height,
            Long durationSeconds,
            String videoCodec,
            Double frameRate,
            Long videoBitrate,
            String audioCodec,
            Integer audioChannels,
            String audioChannelLayout
    ) {
    }
}