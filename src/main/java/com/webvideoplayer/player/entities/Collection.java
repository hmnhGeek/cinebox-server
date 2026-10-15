package com.webvideoplayer.player.entities;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collections")
public class Collection {
    @Id
    private String id;
    private String title;
    private String folder;
    private String tagline;
}
