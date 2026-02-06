package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.util.ArrayList;
import java.util.Random;

/**
 * Manages playlist and shuffle functionality for the music player.
 * Implements true random selection where the next track is determined
 * dynamically when needed.
 */
class PlaylistManager {

  private final ArrayList<Uri> playlist;
  private final Random random;
  private int currentIndex;
  private boolean shuffleEnabled;

  public PlaylistManager() {
    playlist = new ArrayList<>();
    random = new Random();
    currentIndex = 0;
    shuffleEnabled = false;
  }

  /**
   * Set the playlist with a list of URIs
   *
   * @param uris the list of audio URIs
   */
  public void setPlaylist(ArrayList<Uri> uris) {
    playlist.clear();
    if (uris != null) {
      playlist.addAll(uris);
    }
    currentIndex = 0;
  }

  /**
   * Add a single URI to the playlist
   *
   * @param uri the audio URI to add
   */
  public void addToPlaylist(Uri uri) {
    if (uri != null) {
      playlist.add(uri);
    }
  }

  /**
   * Clear the playlist and reset state
   */
  public void clear() {
    playlist.clear();
    currentIndex = 0;
  }

  /**
   * Get the current track URI
   *
   * @return the current track URI, or null if playlist is empty
   */
  public Uri getCurrentTrack() {
    if (playlist.isEmpty()) {
      return null;
    }
    if (currentIndex >= playlist.size()) {
      currentIndex = 0;
    }
    return playlist.get(currentIndex);
  }

  /**
   * Get the next track URI based on shuffle mode.
   * If shuffle is enabled, selects a random track (true random).
   * If shuffle is disabled, moves to the next track sequentially.
   *
   * @return the next track URI, or null if playlist is empty
   */
  public Uri getNextTrack() {
    if (playlist.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      // True random: select any track randomly (can repeat)
      currentIndex = random.nextInt(playlist.size());
    } else {
      // Sequential: move to next track
      currentIndex = (currentIndex + 1) % playlist.size();
    }

    return playlist.get(currentIndex);
  }

  /**
   * Get the previous track URI.
   * If shuffle is enabled, selects a random track (true random).
   * If shuffle is disabled, moves to the previous track sequentially.
   *
   * @return the previous track URI, or null if playlist is empty
   */
  public Uri getPreviousTrack() {
    if (playlist.isEmpty()) {
      return null;
    }

    if (shuffleEnabled) {
      // True random: select any track randomly
      currentIndex = random.nextInt(playlist.size());
    } else {
      // Sequential: move to previous track
      currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
    }

    return playlist.get(currentIndex);
  }

  /**
   * Check if shuffle mode is enabled
   *
   * @return true if shuffle is enabled
   */
  public boolean isShuffleEnabled() {
    return shuffleEnabled;
  }

  /**
   * Set shuffle mode
   *
   * @param enabled true to enable shuffle, false to disable
   */
  public void setShuffleEnabled(boolean enabled) {
    this.shuffleEnabled = enabled;
  }

  /**
   * Toggle shuffle mode
   *
   * @return the new shuffle state
   */
  public boolean toggleShuffle() {
    shuffleEnabled = !shuffleEnabled;
    return shuffleEnabled;
  }

  /**
   * Get the number of tracks in the playlist
   *
   * @return the playlist size
   */
  public int size() {
    return playlist.size();
  }

  /**
   * Check if the playlist is empty
   *
   * @return true if playlist is empty
   */
  public boolean isEmpty() {
    return playlist.isEmpty();
  }

  /**
   * Check if there are more tracks available (for non-looping mode)
   *
   * @return true if not at the last track or shuffle is enabled
   */
  public boolean hasNext() {
    if (playlist.isEmpty()) {
      return false;
    }
    return shuffleEnabled || currentIndex < playlist.size() - 1;
  }

  /**
   * Get the current track index
   *
   * @return the current index
   */
  public int getCurrentIndex() {
    return currentIndex;
  }
}
