package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

/**
 * Scans the parent directory of a given audio file for sibling audio files.
 */
class DirectoryScanner {

  private static final String[] AUDIO_EXTENSIONS = {
    "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus",
    "mid", "midi", "amr", "3gp"
  };

  private final Uri currentUri;
  private final ArrayList audioFiles;
  private final Random random;

  DirectoryScanner(Uri uri) {
    this.currentUri = uri;
    this.audioFiles = new ArrayList();
    this.random = new Random();
    scan();
  }

  private void scan() {
    if (currentUri == null) return;

    var path = currentUri.getPath();
    if (path == null) return;

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return;

    var files = parentDir.listFiles();
    if (files == null) return;

    for (var file : files) {
      if (file.isFile() && isAudioFile(file.getName())) {
        audioFiles.add(file);
      }
    }
  }

  private boolean isAudioFile(String name) {
    var dotIndex = name.lastIndexOf('.');
    if (dotIndex < 0) return false;
    var ext = name.substring(dotIndex + 1).toLowerCase();
    for (var audioExt : AUDIO_EXTENSIONS) {
      if (audioExt.equals(ext)) return true;
    }
    return false;
  }

  /**
   * Returns a random audio file Uri from the same directory,
   * excluding the current file if possible.
   *
   * @return a Uri for a random sibling audio file, or null if none found
   */
  Uri getRandomTrack() {
    if (audioFiles.isEmpty()) return null;

    if (currentUri == null) return null;
    var currentPath = currentUri.getPath();

    // Build a list of candidates excluding the current file
    var candidates = new ArrayList();
    for (var i = 0; i < audioFiles.size(); i++) {
      var file = (File) audioFiles.get(i);
      if (currentPath != null && file.getAbsolutePath().equals(new File(currentPath).getAbsolutePath())) {
        continue;
      }
      candidates.add(file);
    }

    // Fall back to all files if current was the only one
    if (candidates.isEmpty()) {
      candidates = audioFiles;
    }

    var chosen = (File) candidates.get(random.nextInt(candidates.size()));
    return Uri.fromFile(chosen);
  }
}
