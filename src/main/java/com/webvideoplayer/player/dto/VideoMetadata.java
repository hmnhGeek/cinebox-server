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

    private StudioDTO studio;
    private List<StudioDTO> studios;

    private List<PersonDTO> directors;
    private List<PersonDTO> writers;
    private List<TagDTO> tags;
    private List<ActorDTO> actors;
    private List<GenreDTO> genres;

    private String country;
    private String originalCountry;
    private String language;
    private String mpaa;
    private String website;

    private String thumbnailUrl;
}