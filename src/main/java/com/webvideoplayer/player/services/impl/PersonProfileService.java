package com.webvideoplayer.player.services.impl;

import com.webvideoplayer.player.dto.PersonProfileDTO;
import com.webvideoplayer.player.dto.UpdatePersonProfileRequest;
import com.webvideoplayer.player.entities.Person;
import com.webvideoplayer.player.entities.PersonProfile;
import com.webvideoplayer.player.repositories.PersonProfileRepository;
import com.webvideoplayer.player.repositories.PersonRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class PersonProfileService {

    private final PersonRepository personRepository;
    private final PersonProfileRepository personProfileRepository;
    private final PersonProfileStorageService storageService;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public PersonProfileDTO getProfile(String personId) {
        Person person = getPerson(personId);

        return personProfileRepository.findById(personId)
                .map(profile -> toDTO(person, profile))
                .orElseGet(() -> toDTO(person, null));
    }

    @Transactional
    public PersonProfileDTO updateProfile(
            String personId,
            UpdatePersonProfileRequest request) {

        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Profile request is required"
            );
        }

        Person person = getPerson(personId);
        PersonProfile profile = getOrCreateProfile(person);

        profile.setBiography(request.getBiography());

        // The profile is managed by the EntityManager.
        // Hibernate persists changes when the transaction commits.
        return toDTO(person, profile);
    }

    @Transactional
    public PersonProfileDTO uploadImage(
            String personId,
            String imageType,
            MultipartFile file) {

        Person person = getPerson(personId);

        if (!"profile".equals(imageType) && !"hero".equals(imageType)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported image type"
            );
        }

        PersonProfile profile = getOrCreateProfile(person);

        String storedPath = storageService.store(personId, imageType, file);

        if ("profile".equals(imageType)) {
            profile.setProfileImagePath(storedPath);
        } else {
            profile.setHeroImagePath(storedPath);
        }

        // No repository.save() is needed for this managed entity.
        return toDTO(person, profile);
    }

    @Transactional(readOnly = true)
    public Path getImagePath(String personId, String imageType) {
        getPerson(personId);

        PersonProfile profile = personProfileRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Profile image not found"
                ));

        String storedPath;

        if ("profile".equals(imageType)) {
            storedPath = profile.getProfileImagePath();
        } else if ("hero".equals(imageType)) {
            storedPath = profile.getHeroImagePath();
        } else {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Profile image not found"
            );
        }

        return storageService.resolve(storedPath);
    }

    private Person getPerson(String personId) {
        return personRepository.findById(personId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Person not found"
                ));
    }

    private PersonProfile getOrCreateProfile(Person person) {
        return personProfileRepository.findById(person.getId())
                .orElseGet(() -> {
                    PersonProfile profile = new PersonProfile();

                    // @MapsId derives personId from this association.
                    profile.setPerson(person);

                    // Explicitly persist a new entity instead of merging it.
                    entityManager.persist(profile);

                    return profile;
                });
    }

    private PersonProfileDTO toDTO(
            Person person,
            PersonProfile profile) {

        boolean hasProfileImage = profile != null
                && profile.getProfileImagePath() != null
                && !profile.getProfileImagePath().isBlank();

        boolean hasHeroImage = profile != null
                && profile.getHeroImagePath() != null
                && !profile.getHeroImagePath().isBlank();

        return PersonProfileDTO.builder()
                .personId(person.getId())
                .name(person.getName())
                .biography(profile == null ? null : profile.getBiography())
                .profileImageUrl(hasProfileImage
                        ? "/api/v1/people/" + person.getId() + "/profile/image"
                        : null)
                .heroImageUrl(hasHeroImage
                        ? "/api/v1/people/" + person.getId() + "/profile/hero-image"
                        : null)
                .build();
    }
}
