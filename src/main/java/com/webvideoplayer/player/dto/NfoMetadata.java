package com.webvideoplayer.player.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NfoMetadata {
    public String title;
    public String originalTitle;
    public String sortTitle;
    public String plot;
    public String outline;
    public Integer year;
    public String premiered;
    public String releaseDate;
    public Integer runtime;
    public List<String> genres = new ArrayList<>();
    public String country;
    public String originalCountry;
    public String language;
    public String mpaa;
    public List<String> studios = new ArrayList<>();
    public List<String> directors = new ArrayList<>();
    public List<String> writers = new ArrayList<>();
    public List<Actor> actors = new ArrayList<>();
    public List<String> tags = new ArrayList<>();
    public String website;
    public String studio;
}