package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Random;

/**
 * Manages a playlist of audio tracks with shuffle support
 */
public class Playlist {

    private final ArrayList<Uri> tracks;
    private final Random random;
    private int currentIndex;
    private boolean shuffleEnabled;

    public Playlist() {
        tracks = new ArrayList<>();
        random = new Random();
        currentIndex = 0;
        shuffleEnabled = false;
    }

    /**
     * Clear the playlist and add a single track
     */
    public void setTrack(Uri uri) {
        tracks.clear();
        tracks.add(uri);
        currentIndex = 0;
    }

    /**
     * Clear the playlist and add multiple tracks
     */
    public void setTracks(ArrayList<Uri> uris) {
        tracks.clear();
        tracks.addAll(uris);
        currentIndex = 0;
    }

    /**
     * Add a track to the playlist
     */
    public void addTrack(Uri uri) {
        tracks.add(uri);
    }

    /**
     * Get the current track
     */
    public Uri getCurrentTrack() {
        if (tracks.isEmpty()) {
            return null;
        }
        return tracks.get(currentIndex);
    }

    /**
     * Get the next track based on shuffle mode
     * @return the next track Uri, or null if playlist is empty
     */
    public Uri getNextTrack() {
        if (tracks.isEmpty()) {
            return null;
        }
        
        if (tracks.size() == 1) {
            return tracks.get(0);
        }

        if (shuffleEnabled) {
            // True random: pick any track except the current one
            int newIndex;
            do {
                newIndex = random.nextInt(tracks.size());
            } while (newIndex == currentIndex);
            currentIndex = newIndex;
        } else {
            // Sequential: move to next track
            currentIndex = (currentIndex + 1) % tracks.size();
        }
        
        return tracks.get(currentIndex);
    }

    /**
     * Get the previous track
     * @return the previous track Uri, or null if playlist is empty
     */
    public Uri getPreviousTrack() {
        if (tracks.isEmpty()) {
            return null;
        }
        
        if (tracks.size() == 1) {
            return tracks.get(0);
        }

        if (shuffleEnabled) {
            // True random: pick any track except the current one
            int newIndex;
            do {
                newIndex = random.nextInt(tracks.size());
            } while (newIndex == currentIndex);
            currentIndex = newIndex;
        } else {
            // Sequential: move to previous track
            currentIndex = (currentIndex - 1 + tracks.size()) % tracks.size();
        }
        
        return tracks.get(currentIndex);
    }

    /**
     * Check if shuffle mode is enabled
     */
    public boolean isShuffleEnabled() {
        return shuffleEnabled;
    }

    /**
     * Toggle shuffle mode
     */
    public void toggleShuffle() {
        shuffleEnabled = !shuffleEnabled;
    }

    /**
     * Set shuffle mode
     */
    public void setShuffleEnabled(boolean enabled) {
        shuffleEnabled = enabled;
    }

    /**
     * Get the number of tracks in the playlist
     */
    public int size() {
        return tracks.size();
    }

    /**
     * Check if the playlist has more than one track
     */
    public boolean hasMultipleTracks() {
        return tracks.size() > 1;
    }

    /**
     * Check if we're at the last track (for non-looping sequential playback)
     */
    public boolean isAtLastTrack() {
        return currentIndex == tracks.size() - 1;
    }

    /**
     * Get current track index (1-based for display)
     */
    public int getCurrentTrackNumber() {
        return currentIndex + 1;
    }
}
