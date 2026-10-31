
package com.webvideoplayer.player.repositories;

import com.webvideoplayer.player.entities.MediaItem;
import com.webvideoplayer.player.entities.Person;
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

    /**
     * Returns distinct actors from the supplied collections.
     */
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                    ORDER BY LOWER(COALESCE(p.name, '')) ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                    """
    )
    Page<Person> findDistinctActorsInCollections(
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    /**
     * Returns distinct actors whose profiles have a nonblank
     * profile-image path recorded in person_profiles.
     *
     * The filter is applied before pagination.
     */
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND EXISTS (
                          SELECT pp.personId
                          FROM PersonProfile pp
                          WHERE pp.personId = p.id
                            AND pp.profileImagePath IS NOT NULL
                            AND TRIM(pp.profileImagePath) <> ''
                      )
                    ORDER BY LOWER(COALESCE(p.name, '')) ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND EXISTS (
                          SELECT pp.personId
                          FROM PersonProfile pp
                          WHERE pp.personId = p.id
                            AND pp.profileImagePath IS NOT NULL
                            AND TRIM(pp.profileImagePath) <> ''
                      )
                    """
    )
    Page<Person> findDistinctActorsWithProfileImageInCollections(
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
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
            FROM MediaItemPerson mip
            JOIN mip.mediaItem mi
            WHERE mip.person.id = :personId
              AND UPPER(mip.role) = 'DIRECTOR'
              AND mi.collection.id IN :collectionIds
            ORDER BY mi.title ASC
            """)
    Page<MediaItem> findVideosByDirector(
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

    @Query(
            value = """
                    SELECT mi
                    FROM MediaItem mi
                    WHERE mi.collection.id IN :collectionIds
                      AND (
                          LOWER(mi.title)
                              LIKE LOWER(CONCAT('%', :query, '%'))

                          OR EXISTS (
                              SELECT md.id
                              FROM MediaMetadata md
                              WHERE md.mediaItem = mi
                                AND (
                                    LOWER(COALESCE(md.originalTitle, ''))
                                        LIKE LOWER(CONCAT('%', :query, '%'))
                                    OR LOWER(COALESCE(md.sortTitle, ''))
                                        LIKE LOWER(CONCAT('%', :query, '%'))
                                )
                          )

                          OR EXISTS (
                              SELECT mit.id
                              FROM MediaItemTag mit
                              WHERE mit.mediaItem = mi
                                AND LOWER(mit.tag.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT md2.id
                              FROM MediaMetadata md2
                              JOIN md2.genres g
                              WHERE md2.mediaItem = mi
                                AND LOWER(g.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT mip.id
                              FROM MediaItemPerson mip
                              WHERE mip.mediaItem = mi
                                AND UPPER(mip.role) = 'ACTOR'
                                AND LOWER(mip.person.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT mipd.id
                              FROM MediaItemPerson mipd
                              WHERE mipd.mediaItem = mi
                                AND UPPER(mipd.role) = 'DIRECTOR'
                                AND LOWER(mipd.person.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT md3.id
                              FROM MediaMetadata md3
                              JOIN md3.studios s
                              WHERE md3.mediaItem = mi
                                AND LOWER(s.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )
                      )
                    ORDER BY mi.title ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT mi.id)
                    FROM MediaItem mi
                    WHERE mi.collection.id IN :collectionIds
                      AND (
                          LOWER(mi.title)
                              LIKE LOWER(CONCAT('%', :query, '%'))

                          OR EXISTS (
                              SELECT md.id
                              FROM MediaMetadata md
                              WHERE md.mediaItem = mi
                                AND (
                                    LOWER(COALESCE(md.originalTitle, ''))
                                        LIKE LOWER(CONCAT('%', :query, '%'))
                                    OR LOWER(COALESCE(md.sortTitle, ''))
                                        LIKE LOWER(CONCAT('%', :query, '%'))
                                )
                          )

                          OR EXISTS (
                              SELECT mit.id
                              FROM MediaItemTag mit
                              WHERE mit.mediaItem = mi
                                AND LOWER(mit.tag.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT md2.id
                              FROM MediaMetadata md2
                              JOIN md2.genres g
                              WHERE md2.mediaItem = mi
                                AND LOWER(g.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT mip.id
                              FROM MediaItemPerson mip
                              WHERE mip.mediaItem = mi
                                AND UPPER(mip.role) = 'ACTOR'
                                AND LOWER(mip.person.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT mipd.id
                              FROM MediaItemPerson mipd
                              WHERE mipd.mediaItem = mi
                                AND UPPER(mipd.role) = 'DIRECTOR'
                                AND LOWER(mipd.person.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )

                          OR EXISTS (
                              SELECT md3.id
                              FROM MediaMetadata md3
                              JOIN md3.studios s
                              WHERE md3.mediaItem = mi
                                AND LOWER(s.name)
                                    LIKE LOWER(CONCAT('%', :query, '%'))
                          )
                      )
                    """
    )
    Page<MediaItem> searchVideos(
            @Param("query") String query,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    /**
     * Searches distinct actors by name.
     */
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND LOWER(COALESCE(p.name, ''))
                          LIKE LOWER(CONCAT('%', :query, '%'))
                    ORDER BY LOWER(COALESCE(p.name, '')) ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND LOWER(COALESCE(p.name, ''))
                          LIKE LOWER(CONCAT('%', :query, '%'))
                    """
    )
    Page<Person> searchDistinctActorsByNameInCollections(
            @Param("query") String query,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    /**
     * Searches distinct actors by name, restricted to profiles
     * with a nonblank stored profile-image path.
     */
    @Query(
            value = """
                    SELECT DISTINCT p
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND LOWER(COALESCE(p.name, ''))
                          LIKE LOWER(CONCAT('%', :query, '%'))
                      AND EXISTS (
                          SELECT pp.personId
                          FROM PersonProfile pp
                          WHERE pp.personId = p.id
                            AND pp.profileImagePath IS NOT NULL
                            AND TRIM(pp.profileImagePath) <> ''
                      )
                    ORDER BY LOWER(COALESCE(p.name, '')) ASC, p.id ASC
                    """,
            countQuery = """
                    SELECT COUNT(DISTINCT p.id)
                    FROM MediaItemPerson mip
                    JOIN mip.person p
                    JOIN mip.mediaItem mi
                    WHERE UPPER(mip.role) = 'ACTOR'
                      AND mi.collection.id IN :collectionIds
                      AND LOWER(COALESCE(p.name, ''))
                          LIKE LOWER(CONCAT('%', :query, '%'))
                      AND EXISTS (
                          SELECT pp.personId
                          FROM PersonProfile pp
                          WHERE pp.personId = p.id
                            AND pp.profileImagePath IS NOT NULL
                            AND TRIM(pp.profileImagePath) <> ''
                      )
                    """
    )
    Page<Person> searchDistinctActorsWithProfileImageByNameInCollections(
            @Param("query") String query,
            @Param("collectionIds") List<String> collectionIds,
            Pageable pageable
    );

    @Query("""
        SELECT mi
        FROM MediaItem mi
        JOIN FETCH mi.collection
        WHERE mi.id = :mediaItemId
        """)
    Optional<MediaItem> findWithCollectionById(
            @Param("mediaItemId") String mediaItemId
    );
}
