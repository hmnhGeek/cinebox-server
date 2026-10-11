package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.Storage;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StorageRepository extends MongoRepository<Storage, String> {
}
