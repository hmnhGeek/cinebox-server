package com.webvideoplayer.player.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class VideoMetadata {
    private String id;

    private String title;
    private String originalTitle;
    private String sortTitle;

    private String plot;
    private String outline;

    private Integer year;
    private String premiered;
    private String releaseDate;
    private Integer runtime;

    private String studio;
    private List<String> studios;

    private List<String> directors;
    private List<String> writers;
    private List<String> tags;
    private List<Actor> actors;
    private List<String> genres;

    private String country;
    private String originalCountry;
    private String language;
    private String mpaa;
    private String website;

    private String videoPath;
    private String posterPath;
    private String thumbnailUrl;
}