package com.martinmimigames.tinymusicplayer;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

/**
 * Manages a playlist of audio files with shuffle support
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
   * Initialize playlist with a single track and optionally discover sibling audio files
   *
   * @param context  the application context
   * @param audioUri the initial audio URI
   */
  public void initialize(Context context, Uri audioUri) {
    tracks.clear();
    currentIndex = 0;

    // Try to find sibling audio files in the same directory
    if (audioUri != null) {
      discoverSiblingTracks(context, audioUri);
    }

    // If no tracks were discovered, just add the single track
    if (tracks.isEmpty() && audioUri != null) {
      tracks.add(audioUri);
      currentIndex = 0;
    }
  }

  /**
   * Discover audio files in the same directory as the given URI
   */
  private void discoverSiblingTracks(Context context, Uri audioUri) {
    String scheme = audioUri.getScheme();

    if ("file".equals(scheme)) {
      discoverFromFilePath(audioUri);
    } else if ("content".equals(scheme)) {
      discoverFromContentUri(context, audioUri);
    } else {
      // Unknown scheme, just add the single track
      tracks.add(audioUri);
      currentIndex = 0;
    }
  }

  /**
   * Discover audio files from a file:// URI
   */
  private void discoverFromFilePath(Uri audioUri) {
    String path = audioUri.getPath();
    if (path == null) {
      tracks.add(audioUri);
      return;
    }

    File file = new File(path);
    File parentDir = file.getParentFile();

    if (parentDir != null && parentDir.isDirectory()) {
      File[] files = parentDir.listFiles();
      if (files != null) {
        for (File f : files) {
          if (isAudioFile(f.getName())) {
            tracks.add(Uri.fromFile(f));
          }
        }
        // Find the index of the original file
        for (int i = 0; i < tracks.size(); i++) {
          if (tracks.get(i).getPath() != null && 
              tracks.get(i).getPath().equals(path)) {
            currentIndex = i;
            break;
          }
        }
      }
    }

    if (tracks.isEmpty()) {
      tracks.add(audioUri);
      currentIndex = 0;
    }
  }

  /**
   * Discover audio files from a content:// URI
   */
  private void discoverFromContentUri(Context context, Uri audioUri) {
    // For content URIs, we'll try to get the file path and find siblings
    String filePath = getFilePathFromContentUri(context, audioUri);
    
    if (filePath != null) {
      File file = new File(filePath);
      File parentDir = file.getParentFile();
      
      if (parentDir != null && parentDir.isDirectory()) {
        File[] files = parentDir.listFiles();
        if (files != null) {
          for (File f : files) {
            if (isAudioFile(f.getName())) {
              tracks.add(Uri.fromFile(f));
            }
          }
          // Find the index of the original file
          for (int i = 0; i < tracks.size(); i++) {
            String trackPath = tracks.get(i).getPath();
            if (trackPath != null && trackPath.equals(filePath)) {
              currentIndex = i;
              break;
            }
          }
        }
      }
    }

    // If we couldn't discover siblings, just add the original URI
    if (tracks.isEmpty()) {
      tracks.add(audioUri);
      currentIndex = 0;
    }
  }

  /**
   * Try to get the file path from a content URI
   */
  private String getFilePathFromContentUri(Context context, Uri contentUri) {
    String[] projection = {MediaStore.Audio.Media.DATA};
    Cursor cursor = null;
    try {
      ContentResolver resolver = context.getContentResolver();
      cursor = resolver.query(contentUri, projection, null, null, null);
      if (cursor != null && cursor.moveToFirst()) {
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        return cursor.getString(columnIndex);
      }
    } catch (Exception e) {
      // Ignore errors, we'll fall back to single track
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return null;
  }

  /**
   * Check if a filename appears to be an audio file
   */
  private boolean isAudioFile(String filename) {
    if (filename == null) return false;
    String lower = filename.toLowerCase();
    return lower.endsWith(".mp3") ||
           lower.endsWith(".wav") ||
           lower.endsWith(".ogg") ||
           lower.endsWith(".flac") ||
           lower.endsWith(".m4a") ||
           lower.endsWith(".aac") ||
           lower.endsWith(".wma") ||
           lower.endsWith(".opus") ||
           lower.endsWith(".mid") ||
           lower.endsWith(".midi");
  }

  /**
   * Get the current track URI
   */
  public Uri getCurrentTrack() {
    if (tracks.isEmpty()) return null;
    return tracks.get(currentIndex);
  }

  /**
   * Get the next track URI based on shuffle mode
   *
   * @return the next track URI, or null if at end and not looping
   */
  public Uri getNextTrack() {
    if (tracks.isEmpty()) return null;
    
    if (shuffleEnabled) {
      // True random: pick any track except current one (if possible)
      if (tracks.size() > 1) {
        int newIndex;
        do {
          newIndex = random.nextInt(tracks.size());
        } while (newIndex == currentIndex);
        currentIndex = newIndex;
      }
      // If only one track, currentIndex stays the same
    } else {
      // Sequential: move to next track
      currentIndex = (currentIndex + 1) % tracks.size();
    }
    
    return tracks.get(currentIndex);
  }

  /**
   * Get the previous track URI
   *
   * @return the previous track URI
   */
  public Uri getPreviousTrack() {
    if (tracks.isEmpty()) return null;
    
    if (shuffleEnabled) {
      // In shuffle mode, previous also picks a random track
      if (tracks.size() > 1) {
        int newIndex;
        do {
          newIndex = random.nextInt(tracks.size());
        } while (newIndex == currentIndex);
        currentIndex = newIndex;
      }
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
  public int getTrackCount() {
    return tracks.size();
  }

  /**
   * Get the current track index (1-based for display)
   */
  public int getCurrentTrackNumber() {
    return currentIndex + 1;
  }

  /**
   * Check if this is the last track (for non-shuffle, non-loop mode)
   */
  public boolean isLastTrack() {
    return currentIndex == tracks.size() - 1;
  }

  /**
   * Check if this is the first track
   */
  public boolean isFirstTrack() {
    return currentIndex == 0;
  }
}
