package com.webvideoplayer.player.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "storage")
public class Storage {
    @Id
    private String id;
    private String uploadDirectory;
    private String posterDirectory;
    private String thumbnailDirectory;
    private String previewDirectory;
}