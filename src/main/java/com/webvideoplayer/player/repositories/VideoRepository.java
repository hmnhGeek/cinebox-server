package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Video;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends MongoRepository<Video, String> {
    Optional<Video> findByTitle(String title);
    List<Video> findByTitleContainingIgnoreCase(String title);
}