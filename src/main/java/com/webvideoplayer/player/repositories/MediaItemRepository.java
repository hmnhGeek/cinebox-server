
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MediaItemRepository
        extends JpaRepository<MediaItem, String> {

    List<MediaItem> findByCollection_IdOrderByTitleAsc(
            String collectionId
    );

    Optional<MediaItem> findByCollection_IdAndRelativePath(
            String collectionId,
            String relativePath
    );

    boolean existsByCollection_IdAndRelativePath(
            String collectionId,
            String relativePath
    );

    @Query("""
        SELECT DISTINCT mi
        FROM MediaItem mi
        JOIN MediaItemTag mit ON mit.mediaItem = mi
        WHERE mit.tag.id = :tagId
          AND mi.collection.id IN :collectionIds
        ORDER BY mi.title ASC
        """)
    Page<MediaItem> findVideosByTag(
            @Param("tagId") String tagId,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT mi
        FROM MediaMetadata md
        JOIN md.mediaItem mi
        JOIN md.genres g
        WHERE g.id = :genreId
          AND mi.collection.id IN :collectionIds
        ORDER BY mi.title ASC
        """)
    Page<MediaItem> findVideosByGenre(
            @Param("genreId") String genreId,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT mi
        FROM MediaItemPerson mip
        JOIN mip.mediaItem mi
        WHERE mip.person.id = :personId
          AND UPPER(mip.role) = 'ACTOR'
          AND mi.collection.id IN :collectionIds
        ORDER BY mi.title ASC
        """)
    Page<MediaItem> findVideosByActor(
            @Param("personId") String personId,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    @Query("""
        SELECT DISTINCT mi
        FROM MediaMetadata md
        JOIN md.mediaItem mi
        JOIN md.studios s
        WHERE s.id = :studioId
          AND mi.collection.id IN :collectionIds
        ORDER BY mi.title ASC
        """)
    Page<MediaItem> findVideosByStudio(
            @Param("studioId") String studioId,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );
}
