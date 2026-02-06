package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages a playlist of audio tracks with shuffle support
 */
public class Playlist {

  private final List<Uri> tracks;
  private final Random random;
  private int currentIndex;
  private boolean shuffleEnabled;

  public Playlist() {
    tracks = new ArrayList<>();
    random = new Random();
    currentIndex = -1;
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
   * Add multiple tracks to the playlist
   */
  public void setTracks(List<Uri> uris) {
    tracks.clear();
    tracks.addAll(uris);
    currentIndex = 0;
  }

  /**
   * Add a track to the playlist
   */
  public void addTrack(Uri uri) {
    tracks.add(uri);
    if (currentIndex < 0) {
      currentIndex = 0;
    }
  }

  /**
   * Get the current track
   */
  public Uri getCurrentTrack() {
    if (currentIndex >= 0 && currentIndex < tracks.size()) {
      return tracks.get(currentIndex);
    }
    return null;
  }

  /**
   * Get the next track based on shuffle mode
   * @return the next track URI, or null if no more tracks
   */
  public Uri getNextTrack() {
    if (tracks.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      // True random: pick any track randomly (can repeat)
      currentIndex = random.nextInt(tracks.size());
    } else {
      // Sequential: move to next track
      currentIndex++;
      if (currentIndex >= tracks.size()) {
        currentIndex = 0; // Loop back to start
      }
    }

    return getCurrentTrack();
  }

  /**
   * Get the previous track
   * @return the previous track URI
   */
  public Uri getPreviousTrack() {
    if (tracks.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      // In shuffle mode, previous also picks randomly
      currentIndex = random.nextInt(tracks.size());
    } else {
      // Sequential: move to previous track
      currentIndex--;
      if (currentIndex < 0) {
        currentIndex = tracks.size() - 1; // Loop to end
      }
    }

    return getCurrentTrack();
  }

  /**
   * Check if shuffle is enabled
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
   * Check if playlist has more than one track
   */
  public boolean hasMultipleTracks() {
    return tracks.size() > 1;
  }

  /**
   * Check if playlist is empty
   */
  public boolean isEmpty() {
    return tracks.isEmpty();
  }
}
