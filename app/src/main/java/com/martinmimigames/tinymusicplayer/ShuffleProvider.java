package com.martinmimigames.tinymusicplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.Random;

/**
 * Provides random sibling audio files for shuffle playback
 */
class ShuffleProvider {

  private static final String[] AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac",
    ".wma", ".opus", ".mid", ".midi", ".3gp", ".mp4",
    ".mkv", ".webm"
  };

  private static final Random random = new Random();

  /**
   * Get a random sibling audio Uri from the same directory as the current file.
   *
   * @param context  the application context
   * @param currentUri the Uri of the currently playing file
   * @return a random sibling audio Uri, or null if none found
   */
  static Uri getNextShuffleUri(Context context, Uri currentUri) {
    if (currentUri == null) return null;

    var scheme = currentUri.getScheme();

    if ("file".equals(scheme)) {
      return getNextFromFile(currentUri);
    }

    if ("content".equals(scheme)) {
      var result = getNextFromContent(context, currentUri);
      if (result != null) return result;
      /* fall back to file-based approach */
      return getNextFromContentFallback(context, currentUri);
    }

    return null;
  }

  /**
   * Get next random audio file from file:// Uri
   */
  private static Uri getNextFromFile(Uri currentUri) {
    var path = currentUri.getPath();
    if (path == null) return null;

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return null;

    var files = parentDir.listFiles();
    if (files == null) return null;

    var siblings = new ArrayList<File>();
    for (var file : files) {
      if (file.isFile() && isAudioFile(file.getName()) && !file.equals(currentFile)) {
        siblings.add(file);
      }
    }

    if (siblings.isEmpty()) return null;

    return Uri.fromFile(siblings.get(random.nextInt(siblings.size())));
  }

  /**
   * Get next random audio file from content:// Uri using MediaStore
   */
  private static Uri getNextFromContent(Context context, Uri currentUri) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
      return null;
    }

    try {
      /* first, get the file path of the current Uri */
      String currentPath = null;
      Cursor cursor = context.getContentResolver().query(
        currentUri,
        new String[]{MediaStore.Audio.Media.DATA},
        null, null, null
      );
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          currentPath = cursor.getString(0);
        }
        cursor.close();
      }

      if (currentPath == null) return null;

      var currentFile = new File(currentPath);
      var parentPath = currentFile.getParent();
      if (parentPath == null) return null;

      /* query for all audio files in the same directory */
      var selection = MediaStore.Audio.Media.DATA + " LIKE ? AND " +
        MediaStore.Audio.Media.DATA + " NOT LIKE ? AND " +
        MediaStore.Audio.Media.DATA + " != ?";
      var selectionArgs = new String[]{
        parentPath + "/%",
        parentPath + "/%/%",
        currentPath
      };

      cursor = context.getContentResolver().query(
        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
        new String[]{MediaStore.Audio.Media._ID},
        selection, selectionArgs, null
      );

      if (cursor == null) return null;

      var ids = new ArrayList<Long>();
      while (cursor.moveToNext()) {
        ids.add(cursor.getLong(0));
      }
      cursor.close();

      if (ids.isEmpty()) return null;

      var id = ids.get(random.nextInt(ids.size()));
      return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Fallback: try to extract file path from content Uri and use file listing
   */
  private static Uri getNextFromContentFallback(Context context, Uri currentUri) {
    try {
      String path = null;
      Cursor cursor = context.getContentResolver().query(
        currentUri,
        new String[]{"_data"},
        null, null, null
      );
      if (cursor != null) {
        if (cursor.moveToFirst()) {
          path = cursor.getString(0);
        }
        cursor.close();
      }

      if (path == null) return null;

      return getNextFromFile(Uri.fromFile(new File(path)));
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Check if a filename has an audio file extension
   */
  private static boolean isAudioFile(String name) {
    var lowerName = name.toLowerCase();
    for (var ext : AUDIO_EXTENSIONS) {
      if (lowerName.endsWith(ext)) return true;
    }
    return false;
  }
}
