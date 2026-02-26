package com.martinmimigames.tinymusicplayer;

import android.net.Uri;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

/**
 * utility class for discovering audio files in a directory
 */
class AudioFileHelper {

  private static final String[] AUDIO_EXTENSIONS = {
    "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus",
    "mid", "midi", "amr", "3gp", "3gpp"
  };

  private static final Random random = new Random();

  /**
   * get a random sibling audio file from the same directory
   *
   * @param currentUri the Uri of the currently playing audio file
   * @return a Uri pointing to a random sibling audio file, or null if none found
   */
  static Uri getRandomSiblingAudio(Uri currentUri) {
    if (currentUri == null || currentUri.getPath() == null) return null;

    var currentFile = new File(currentUri.getPath());
    var directory = currentFile.getParentFile();
    if (directory == null || !directory.isDirectory()) return null;

    var files = directory.listFiles();
    if (files == null) return null;

    var candidates = new ArrayList<File>();
    for (var file : files) {
      if (!file.isFile()) continue;
      if (file.equals(currentFile)) continue;
      if (isAudioFile(file.getName())) {
        candidates.add(file);
      }
    }

    if (candidates.isEmpty()) return null;

    var chosen = candidates.get(random.nextInt(candidates.size()));
    return Uri.fromFile(chosen);
  }

  /**
   * check if a filename has an audio extension
   *
   * @param name the filename to check
   * @return true if the file has a recognized audio extension
   */
  private static boolean isAudioFile(String name) {
    var lower = name.toLowerCase();
    for (var ext : AUDIO_EXTENSIONS) {
      if (lower.endsWith("." + ext)) return true;
    }
    return false;
  }
}
