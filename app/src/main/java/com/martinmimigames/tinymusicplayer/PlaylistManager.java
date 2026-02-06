package com.martinmimigames.tinymusicplayer;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Manages playlist and shuffle functionality for the music player.
 * Builds a playlist from audio files in the same directory as the selected file.
 */
class PlaylistManager {

  private final Context context;
  private final List<Uri> playlist;
  private final Random random;
  private int currentIndex;
  private boolean shuffleEnabled;

  public PlaylistManager(Context context) {
    this.context = context;
    this.playlist = new ArrayList<>();
    this.random = new Random();
    this.currentIndex = 0;
    this.shuffleEnabled = false;
  }

  /**
   * Initialize playlist from the given URI.
   * Attempts to find other audio files in the same directory.
   *
   * @param initialUri the URI of the initially selected audio file
   */
  public void initializePlaylist(Uri initialUri) {
    playlist.clear();
    currentIndex = 0;

    // Try to build playlist from directory
    if (initialUri != null) {
      String scheme = initialUri.getScheme();
      
      if ("file".equals(scheme)) {
        // File URI - scan directory
        buildPlaylistFromFileUri(initialUri);
      } else if ("content".equals(scheme)) {
        // Content URI - try to get related files
        buildPlaylistFromContentUri(initialUri);
      }
      
      // If we couldn't build a playlist, just use the single file
      if (playlist.isEmpty()) {
        playlist.add(initialUri);
        currentIndex = 0;
      }
    }
  }

  /**
   * Build playlist from a file:// URI by scanning the parent directory
   */
  private void buildPlaylistFromFileUri(Uri fileUri) {
    String path = fileUri.getPath();
    if (path == null) return;

    File file = new File(path);
    File parentDir = file.getParentFile();
    
    if (parentDir != null && parentDir.isDirectory()) {
      File[] files = parentDir.listFiles();
      if (files != null) {
        for (File f : files) {
          if (isAudioFile(f.getName())) {
            Uri uri = Uri.fromFile(f);
            playlist.add(uri);
            // Track the index of the originally selected file
            if (f.getAbsolutePath().equals(file.getAbsolutePath())) {
              currentIndex = playlist.size() - 1;
            }
          }
        }
      }
    }
  }

  /**
   * Build playlist from a content:// URI
   * For content URIs, we try to query for files in the same album/folder
   */
  private void buildPlaylistFromContentUri(Uri contentUri) {
    // For content URIs, we'll just use the single file
    // as querying related files requires more complex MediaStore queries
    // and may not work reliably across different content providers
    playlist.add(contentUri);
    currentIndex = 0;
  }

  /**
   * Check if a filename appears to be an audio file based on extension
   */
  private boolean isAudioFile(String filename) {
    if (filename == null) return false;
    String lower = filename.toLowerCase();
    return lower.endsWith(".mp3") ||
           lower.endsWith(".wav") ||
           lower.endsWith(".ogg") ||
           lower.endsWith(".flac") ||
           lower.endsWith(".aac") ||
           lower.endsWith(".m4a") ||
           lower.endsWith(".wma") ||
           lower.endsWith(".opus") ||
           lower.endsWith(".mid") ||
           lower.endsWith(".midi");
  }

  /**
   * Get the current track URI
   */
  public Uri getCurrentTrack() {
    if (playlist.isEmpty()) return null;
    return playlist.get(currentIndex);
  }

  /**
   * Get the next track URI based on shuffle mode
   *
   * @return the next track URI, or null if at end of playlist (non-shuffle, non-loop)
   */
  public Uri getNextTrack() {
    if (playlist.isEmpty()) return null;
    
    if (shuffleEnabled) {
      // True random: pick any track except current (if possible)
      if (playlist.size() > 1) {
        int newIndex;
        do {
          newIndex = random.nextInt(playlist.size());
        } while (newIndex == currentIndex);
        currentIndex = newIndex;
      }
      // If only one track, currentIndex stays the same
    } else {
      // Sequential: move to next track
      currentIndex = (currentIndex + 1) % playlist.size();
    }
    
    return playlist.get(currentIndex);
  }

  /**
   * Get the previous track URI
   *
   * @return the previous track URI
   */
  public Uri getPreviousTrack() {
    if (playlist.isEmpty()) return null;
    
    if (shuffleEnabled) {
      // In shuffle mode, previous also picks random
      if (playlist.size() > 1) {
        int newIndex;
        do {
          newIndex = random.nextInt(playlist.size());
        } while (newIndex == currentIndex);
        currentIndex = newIndex;
      }
    } else {
      // Sequential: move to previous track
      currentIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
    }
    
    return playlist.get(currentIndex);
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
  public int getPlaylistSize() {
    return playlist.size();
  }

  /**
   * Check if there are multiple tracks (shuffle makes sense)
   */
  public boolean hasMultipleTracks() {
    return playlist.size() > 1;
  }

  /**
   * Get current track index (1-based for display)
   */
  public int getCurrentTrackNumber() {
    return currentIndex + 1;
  }
}
