package com.martinmimigames.tinymusicplayer;

import android.content.ContentUris;
import android.content.Context;
import android.net.Uri;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Random;

final class ShuffleProvider {

  private static final Random random = new Random();

  /**
   * Query MediaStore for a random audio file Uri.
   *
   * @param context the context for content resolver access
   * @return a random audio Uri, or null if none found or on error
   */
  static Uri getRandomAudioUri(Context context) {
    try {
      var uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
      var projection = new String[]{MediaStore.Audio.Media._ID};
      var cursor = context.getContentResolver().query(uri, projection, null, null, null);
      if (cursor == null) return null;

      var ids = new ArrayList<Long>();
      while (cursor.moveToNext()) {
        ids.add(cursor.getLong(0));
      }
      cursor.close();

      if (ids.isEmpty()) return null;

      var id = ids.get(random.nextInt(ids.size()));
      return ContentUris.withAppendedId(uri, id);
    } catch (Exception e) {
      return null;
    }
  }
}
