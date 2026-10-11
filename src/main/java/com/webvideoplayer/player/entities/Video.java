package com.webvideoplayer.player.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "videos")
public class Video {
    @Id
    private String id;
    private String title;
    private String description;
    private String fileName;
    private String filePath;
    private String previewPath;
    private String thumbnailPath;
    private String posterPath;
    private String contentType;
    private Long duration;
    private Instant createdAt;
}