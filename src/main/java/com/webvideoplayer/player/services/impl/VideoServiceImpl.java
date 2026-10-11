package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.entities.Storage;
import com.webvideoplayer.player.entities.Video;
import com.webvideoplayer.player.repositories.StorageRepository;
import com.webvideoplayer.player.repositories.VideoRepository;
import com.webvideoplayer.player.services.VideoPreviewService;
import com.webvideoplayer.player.services.VideoService;
import com.webvideoplayer.player.utils.VideoPreviewUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class VideoServiceImpl implements VideoService {
    private final StorageRepository storageRepository;
    private final VideoRepository videoRepository;
    private final VideoPreviewService videoPreviewService;

    public VideoServiceImpl(StorageRepository storageRepository, VideoRepository videoRepository, VideoPreviewService videoPreviewService) {
        this.storageRepository = storageRepository;
        this.videoRepository = videoRepository;
        this.videoPreviewService = videoPreviewService;
    }

    @Override
    public Video uploadVideo(MultipartFile file, MultipartFile poster, MultipartFile thumbnail, String title, String description) throws IOException, InterruptedException {
        Storage storage = storageRepository.findAll().getFirst();
        String uploadDirectory = storage.getUploadDirectory();
        String previewDirectory = storage.getPreviewDirectory();
        Path posterFilePath = uploadPoster(poster, storage);
        Path thumbnailFilePath = uploadThumbnail(thumbnail, storage);

        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();

        Path uploadPath = Paths.get(uploadDirectory);
        Files.createDirectories(uploadPath);
        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath);

        Video uploadedVideo = new Video();
        uploadedVideo.setTitle(title);
        uploadedVideo.setDescription(description);
        uploadedVideo.setFileName(filename);
        uploadedVideo.setFilePath(filePath.toString());
        uploadedVideo.setPosterPath(posterFilePath.toString());
        uploadedVideo.setThumbnailPath(thumbnailFilePath.toString());
        uploadedVideo.setContentType(contentType);
        uploadedVideo.setDuration(getVideoDuration(filePath));
        uploadedVideo.setCreatedAt(Instant.now());
        this.videoRepository.save(uploadedVideo);

        videoPreviewService.generatePreview(
                uploadedVideo.getId(),
                filePath,
                Path.of(previewDirectory)
        );
        return uploadedVideo;
    }

    @Override
    public List<Video> getAllVideos() {
        return this.videoRepository.findAll();
    }

    @Override
    public Page<Video> getVideos(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt")));
        return this.videoRepository.findAll(pageable);
    }

    @Override
    public Resource getResource(String id, boolean downloadThumbnail) throws MalformedURLException {
        Video video = videoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Video not found"));
        Path path;
        if (downloadThumbnail) {
            path = Paths.get(video.getThumbnailPath());
        } else {
            path = Paths.get(video.getPosterPath());
        }
        Resource resource = new UrlResource(path.toUri());
        return resource;
    }

    @Override
    public Optional<Video> getVideoById(String id) {
        return videoRepository.findById(id);
    }

    private static Path uploadPoster(MultipartFile poster, Storage storage) throws IOException {
        String posterDirectory = storage.getPosterDirectory();
        Path posterUploadPath = Paths.get(posterDirectory);
        Path posterFilePath = posterUploadPath.resolve(poster.getOriginalFilename());
        Files.createDirectories(posterUploadPath);
        poster.transferTo(posterFilePath);
        return posterFilePath;
    }

    private static Path uploadThumbnail(MultipartFile thumbnail, Storage storage) throws IOException {
        String thumbnailDirectory = storage.getThumbnailDirectory();
        Path thumbnailUploadPath = Paths.get(thumbnailDirectory);
        Path thumbnailFilePath = thumbnailUploadPath.resolve(thumbnail.getOriginalFilename());
        Files.createDirectories(thumbnailUploadPath);
        thumbnail.transferTo(thumbnailFilePath);
        return thumbnailFilePath;
    }

    private long getVideoDuration(Path filePath)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                filePath.toString()
        );
        Process process = processBuilder.start();
        String output = new String(
                process.getInputStream().readAllBytes()
        ).trim();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Unable to determine video duration");
        }
        return Math.round(Double.parseDouble(output));
    }
}
