package com.webvideoplayer.player.services.impl;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TechnicalInfoJobRegistry {

    private final Set<String> activeJobs = ConcurrentHashMap.newKeySet();

    public boolean register(String mediaItemId) {
        return activeJobs.add(mediaItemId);
    }

    public void unregister(String mediaItemId) {
        activeJobs.remove(mediaItemId);
    }

    public boolean isRegistered(String mediaItemId) {
        return activeJobs.contains(mediaItemId);
    }
}