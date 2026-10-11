package com.webvideoplayer.player.services;

import com.webvideoplayer.player.entities.Video;
import com.webvideoplayer.player.repositories.VideoRepository;
import com.webvideoplayer.player.utils.VideoPreviewUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
public class VideoPreviewService {

    private final VideoRepository videoRepository;

    public VideoPreviewService(VideoRepository videoRepository) {
        this.videoRepository = videoRepository;
    }

    @Async
    public void generatePreview(
            String videoId,
            Path videoPath,
            Path previewDirectory
    ) {
        try {
            Video video = videoRepository.findById(videoId)
                    .orElseThrow(() -> new RuntimeException("Video not found"));

            Path previewPath = VideoPreviewUtils.generatePreview(
                    videoPath,
                    previewDirectory,
                    video.getDuration()
            );

            video.setPreviewPath(previewPath.toString());
            videoRepository.save(video);

        } catch (Exception e) {
            // log error
        }
    }
}