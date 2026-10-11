package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Collection;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CollectionRepository extends MongoRepository<Collection, String> {

    boolean existsByFolder(String folder);

    Optional<Collection> findByFolder(String folder);
}