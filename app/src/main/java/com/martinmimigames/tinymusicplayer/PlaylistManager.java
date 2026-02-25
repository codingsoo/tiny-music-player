package com.martinmimigames.tinymusicplayer;

import android.content.Context;
import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

/**
 * Discovers audio files in the same directory as the currently playing file
 * and provides random next-track selection for shuffle mode.
 */
class PlaylistManager {

  private static final String[] AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a", ".wma", ".opus", ".mid", ".midi"
  };

  private final ArrayList<Uri> tracks;
  private final Random random;

  /**
   * Create a PlaylistManager that discovers audio files in the same directory.
   *
   * @param context  the application context
   * @param uri      the URI of the currently playing file
   */
  PlaylistManager(Context context, Uri uri) {
    tracks = new ArrayList<Uri>();
    random = new Random();

    var scheme = uri.getScheme();
    if ("file".equals(scheme)) {
      discoverFromFile(uri);
    } else if ("content".equals(scheme)) {
      discoverFromContent(uri);
    } else {
      // Unknown scheme, single-track mode
      tracks.add(uri);
    }
  }

  /**
   * Discover audio files from a file:// URI by listing the parent directory.
   */
  private void discoverFromFile(Uri uri) {
    var path = uri.getPath();
    if (path == null) {
      tracks.add(uri);
      return;
    }
    var file = new File(path);
    var parent = file.getParentFile();
    if (parent == null || !parent.isDirectory()) {
      tracks.add(uri);
      return;
    }
    var files = parent.listFiles();
    if (files == null || files.length == 0) {
      tracks.add(uri);
      return;
    }
    for (var f : files) {
      if (f.isFile() && isAudioFile(f.getName())) {
        tracks.add(Uri.fromFile(f));
      }
    }
    // If nothing was found (shouldn't happen since current file is audio), add original
    if (tracks.isEmpty()) {
      tracks.add(uri);
    }
  }

  /**
   * Attempt to discover audio files from a content:// URI.
   * Many content URIs contain the real file path; try to extract it.
   * Falls back to single-track mode if not possible.
   */
  private void discoverFromContent(Uri uri) {
    var path = uri.getPath();
    if (path != null) {
      var file = new File(path);
      var parent = file.getParentFile();
      if (parent != null && parent.isDirectory()) {
        var files = parent.listFiles();
        if (files != null && files.length > 0) {
          for (var f : files) {
            if (f.isFile() && isAudioFile(f.getName())) {
              tracks.add(Uri.fromFile(f));
            }
          }
          if (!tracks.isEmpty()) {
            return;
          }
        }
      }
    }
    // Fall back to single-track mode
    tracks.add(uri);
  }

  /**
   * Check if a filename has a known audio extension.
   */
  private static boolean isAudioFile(String name) {
    var lower = name.toLowerCase(Locale.US);
    for (var ext : AUDIO_EXTENSIONS) {
      if (lower.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Get a random track from the playlist, excluding the current one.
   *
   * @param current the currently playing URI
   * @return a random URI different from current, or null if only one track exists
   */
  Uri getRandomNext(Uri current) {
    if (tracks.size() <= 1) {
      return null;
    }
    // Build a list excluding current
    var candidates = new ArrayList<Uri>();
    for (var track : tracks) {
      if (!track.equals(current)) {
        candidates.add(track);
      }
    }
    if (candidates.isEmpty()) {
      return null;
    }
    return candidates.get(random.nextInt(candidates.size()));
  }

  /**
   * Check if the playlist has more than one track.
   */
  boolean hasMultipleTracks() {
    return tracks.size() > 1;
  }
}
