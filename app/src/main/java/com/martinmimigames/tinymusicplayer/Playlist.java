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
 * Manages a playlist of audio tracks with shuffle support
 */
class Playlist {

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
   * Initialize playlist from a single URI by scanning the parent directory
   * for other audio files
   *
   * @param context the application context
   * @param initialUri the initially selected audio file
   */
  public void initFromUri(Context context, Uri initialUri) {
    tracks.clear();
    currentIndex = 0;

    // Always add the initial track
    tracks.add(initialUri);

    // Try to find sibling audio files in the same directory
    try {
      String scheme = initialUri.getScheme();
      
      if ("file".equals(scheme)) {
        // For file:// URIs, scan the directory
        scanDirectory(initialUri);
      } else if ("content".equals(scheme)) {
        // For content:// URIs, try to query for sibling files
        scanContentUri(context, initialUri);
      }
    } catch (Exception e) {
      // If scanning fails, we still have the initial track
    }
  }

  /**
   * Scan directory for audio files when given a file:// URI
   */
  private void scanDirectory(Uri fileUri) {
    File file = new File(fileUri.getPath());
    File parentDir = file.getParentFile();
    
    if (parentDir != null && parentDir.isDirectory()) {
      File[] files = parentDir.listFiles();
      if (files != null) {
        tracks.clear();
        int newIndex = 0;
        int index = 0;
        
        for (File f : files) {
          if (isAudioFile(f.getName())) {
            Uri uri = Uri.fromFile(f);
            tracks.add(uri);
            if (f.getAbsolutePath().equals(file.getAbsolutePath())) {
              newIndex = index;
            }
            index++;
          }
        }
        currentIndex = newIndex;
      }
    }
  }

  /**
   * Try to scan for sibling files when given a content:// URI
   */
  private void scanContentUri(Context context, Uri contentUri) {
    // For content URIs, we try to get the file path and scan the directory
    // This may not work for all content providers
    try {
      ContentResolver resolver = context.getContentResolver();
      String[] projection = {MediaStore.Audio.Media.DATA};
      
      Cursor cursor = resolver.query(contentUri, projection, null, null, null);
      if (cursor != null) {
        try {
          if (cursor.moveToFirst()) {
            int columnIndex = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            if (columnIndex >= 0) {
              String filePath = cursor.getString(columnIndex);
              if (filePath != null) {
                Uri fileUri = Uri.fromFile(new File(filePath));
                scanDirectory(fileUri);
              }
            }
          }
        } finally {
          cursor.close();
        }
      }
    } catch (Exception e) {
      // Scanning failed, keep the single track
    }
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
    if (tracks.isEmpty()) return null;
    return tracks.get(currentIndex);
  }

  /**
   * Get the next track URI based on shuffle mode
   * 
   * @return the next track URI, or null if playlist is empty
   */
  public Uri getNextTrack() {
    if (tracks.isEmpty()) return null;
    
    if (tracks.size() == 1) {
      return tracks.get(0);
    }
    
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      int newIndex;
      do {
        newIndex = random.nextInt(tracks.size());
      } while (newIndex == currentIndex && tracks.size() > 1);
      currentIndex = newIndex;
    } else {
      // Sequential: move to next track
      currentIndex = (currentIndex + 1) % tracks.size();
    }
    
    return tracks.get(currentIndex);
  }

  /**
   * Get the previous track URI based on shuffle mode
   * 
   * @return the previous track URI, or null if playlist is empty
   */
  public Uri getPreviousTrack() {
    if (tracks.isEmpty()) return null;
    
    if (tracks.size() == 1) {
      return tracks.get(0);
    }
    
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      int newIndex;
      do {
        newIndex = random.nextInt(tracks.size());
      } while (newIndex == currentIndex && tracks.size() > 1);
      currentIndex = newIndex;
    } else {
      // Sequential: move to previous track
      currentIndex = (currentIndex - 1 + tracks.size()) % tracks.size();
    }
    
    return tracks.get(currentIndex);
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
   * Check if shuffle is enabled
   */
  public boolean isShuffleEnabled() {
    return shuffleEnabled;
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
   * Check if playlist has multiple tracks
   */
  public boolean hasMultipleTracks() {
    return tracks.size() > 1;
  }
}
