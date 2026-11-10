package com.webvideoplayer.player.enums;

public enum ApplicationSettingKey {

    PLAYBACK_DEFAULT_MUTED(
            "playback.defaultMuted",
            true
    ),

    DISCOVERY_ACTORS_SHOW_ONLY_WITH_PROFILE(
            "discovery.actors.showOnlyWithProfile",
            false
    ),

    LIBRARY_EXPAND_TECHNICAL_INFO(
            "library.expandTechnicalInfo",
            false
    ),

    HOME_SHOW_MEDIA_BAR(
            "home.showMediaBar",
            true
    );

    private final String key;
    private final boolean defaultValue;

    ApplicationSettingKey(String key, boolean defaultValue) {
        this.key = key;
        this.defaultValue = defaultValue;
    }

    public String getKey() {
        return key;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}