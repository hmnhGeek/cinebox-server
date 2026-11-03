package com.webvideoplayer.player.dto;

import java.util.Map;

public record ApplicationSettingsDTO(
        PlaybackSettings playback,
        DiscoverySettings discovery,
        LibrarySettings library
) {

    public record PlaybackSettings(
            boolean defaultMuted
    ) {
    }

    public record DiscoverySettings(
            ActorSettings actors
    ) {
    }

    public record ActorSettings(
            boolean showOnlyWithProfile
    ) {
    }

    public record LibrarySettings(
            boolean expandTechnicalInfo
    ) {
    }

    public static ApplicationSettingsDTO from(
            Map<String, Boolean> settings
    ) {
        return new ApplicationSettingsDTO(
                new PlaybackSettings(
                        settings.get("playback.defaultMuted")
                ),
                new DiscoverySettings(
                        new ActorSettings(
                                settings.get("discovery.actors.showOnlyWithProfile")
                        )
                ),
                new LibrarySettings(
                        settings.get("library.expandTechnicalInfo")
                )
        );
    }
}