package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollectionRepository extends JpaRepository<Collection, String> {
    boolean existsByFolder(String folder);
    Optional<Collection> findByFolder(String folder);
}