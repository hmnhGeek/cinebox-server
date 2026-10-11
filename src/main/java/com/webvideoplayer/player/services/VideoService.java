package com.webvideoplayer.player.services;

import com.webvideoplayer.player.entities.Video;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;

public interface VideoService {
    Video uploadVideo(MultipartFile file, MultipartFile poster, MultipartFile thumbnail, String title, String description) throws IOException, InterruptedException;
    List<Video> getAllVideos();
    Page<Video> getVideos(int page, int size);
    Resource getResource(String id, boolean downloadThumbnail) throws MalformedURLException;
    Optional<Video> getVideoById(String id);
}
