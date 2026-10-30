
package com.webvideoplayer.player.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@Getter
@Setter
@ConfigurationProperties(prefix = "cinebox.person-profiles.storage")
public class PersonProfileStorageProperties {

    /**
     * Root directory for person profile images.
     * This directory must already exist and be accessible.
     */
    private Path root = Path.of("./data/person-profiles");

    /**
     * Relative marker path that confirms the expected storage is available.
     * Create this file manually in the configured storage root.
     */
    private String markerFileName = ".cinebox-profile-storage";
}
