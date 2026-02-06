package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages a playlist of audio tracks with shuffle support.
 */
class Playlist {

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
   * Clear the playlist and add a single track.
   *
   * @param uri the audio URI to add
   */
  public void setTrack(Uri uri) {
    tracks.clear();
    tracks.add(uri);
    currentIndex = 0;
  }

  /**
   * Add a track to the playlist.
   *
   * @param uri the audio URI to add
   */
  public void addTrack(Uri uri) {
    tracks.add(uri);
    if (currentIndex < 0) {
      currentIndex = 0;
    }
  }

  /**
   * Get the current track URI.
   *
   * @return the current track URI, or null if playlist is empty
   */
  public Uri getCurrentTrack() {
    if (currentIndex >= 0 && currentIndex < tracks.size()) {
      return tracks.get(currentIndex);
    }
    return null;
  }

  /**
   * Get the next track based on shuffle mode.
   * In shuffle mode, selects a random track different from the current one.
   * In normal mode, moves to the next track in sequence.
   *
   * @return the next track URI, or null if no next track available
   */
  public Uri getNextTrack() {
    if (tracks.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      currentIndex = getRandomIndex();
    } else {
      currentIndex++;
      if (currentIndex >= tracks.size()) {
        currentIndex = 0; // wrap around to beginning
      }
    }

    return getCurrentTrack();
  }

  /**
   * Get the previous track.
   * In shuffle mode, selects a random track different from the current one.
   * In normal mode, moves to the previous track in sequence.
   *
   * @return the previous track URI, or null if no previous track available
   */
  public Uri getPreviousTrack() {
    if (tracks.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      currentIndex = getRandomIndex();
    } else {
      currentIndex--;
      if (currentIndex < 0) {
        currentIndex = tracks.size() - 1; // wrap around to end
      }
    }

    return getCurrentTrack();
  }

  /**
   * Get a random index different from the current one (if possible).
   *
   * @return a random index
   */
  private int getRandomIndex() {
    if (tracks.size() <= 1) {
      return 0;
    }

    int newIndex;
    do {
      newIndex = random.nextInt(tracks.size());
    } while (newIndex == currentIndex);

    return newIndex;
  }

  /**
   * Check if shuffle mode is enabled.
   *
   * @return true if shuffle is enabled
   */
  public boolean isShuffleEnabled() {
    return shuffleEnabled;
  }

  /**
   * Toggle shuffle mode.
   */
  public void toggleShuffle() {
    shuffleEnabled = !shuffleEnabled;
  }

  /**
   * Set shuffle mode.
   *
   * @param enabled true to enable shuffle
   */
  public void setShuffleEnabled(boolean enabled) {
    shuffleEnabled = enabled;
  }

  /**
   * Check if there are multiple tracks in the playlist.
   *
   * @return true if there are more than one track
   */
  public boolean hasMultipleTracks() {
    return tracks.size() > 1;
  }

  /**
   * Get the number of tracks in the playlist.
   *
   * @return the number of tracks
   */
  public int size() {
    return tracks.size();
  }

  /**
   * Clear the playlist.
   */
  public void clear() {
    tracks.clear();
    currentIndex = -1;
  }
}
