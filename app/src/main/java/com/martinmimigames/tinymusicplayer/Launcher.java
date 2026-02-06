package com.martinmimigames.tinymusicplayer;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import java.util.ArrayList;

/**
 * activity for controlling the playback by invoking different logics based on incoming intents
 */
public class Launcher extends Activity {


  static final String TYPE = "type";
  static final byte NULL = 0;
  static final byte PLAY_PAUSE = 1;
  static final byte KILL = 2;
  static final byte PLAY = 3;
  static final byte PAUSE = 4;

  static final byte LOOP = 5;
  static final byte SHUFFLE = 6;
  static final byte NEXT = 7;
  static final byte PREVIOUS = 8;

  private static final int REQUEST_CODE = 3216487;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    if (!Intent.ACTION_VIEW.equals(getIntent().getAction())
      && !Intent.ACTION_SEND.equals(getIntent().getAction())
      && !Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction())) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        this.getPackageManager()
          .checkPermission(
            Manifest.permission.POST_NOTIFICATIONS, this.getPackageName())
          != PackageManager.PERMISSION_GRANTED) {
        final var intent = new Intent();
        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
        intent.putExtra(Settings.EXTRA_APP_PACKAGE, this.getPackageName());
        this.startActivity(intent);
        finish();
        return;
      }

      /* request a file from the system */
      var intent = new Intent(Intent.ACTION_GET_CONTENT);
      intent.setType("audio/*"); // intent type to filter application based on your requirement
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // Allow multiple file selection
      }
      startActivityForResult(intent, REQUEST_CODE);
      return;
    }
    onIntent(getIntent());
  }

  /**
   * redirect call to actual logic
   */
  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    onIntent(intent);
  }

  /**
   * restarts service
   */
  private void onIntent(Intent intent) {
    intent.setClass(this, Service.class);
    stopService(intent);
    startService(intent);
    /* does not need to keep this activity */
    finish();
  }

  /**
   * call service control on receiving file
   */
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    /* if result unusable, discard */
    if (requestCode == REQUEST_CODE && resultCode == Activity.RESULT_OK) {
      /* Check if multiple files were selected */
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && intent.getClipData() != null) {
        ClipData clipData = intent.getClipData();
        ArrayList<Uri> uris = new ArrayList<>();
        for (int i = 0; i < clipData.getItemCount(); i++) {
          uris.add(clipData.getItemAt(i).getUri());
        }
        if (uris.size() > 1) {
          /* redirect to service with multiple files */
          Intent serviceIntent = new Intent(Intent.ACTION_SEND_MULTIPLE);
          serviceIntent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
          onIntent(serviceIntent);
          return;
        }
      }
      /* Single file selected - redirect to service */
      intent.setAction(Intent.ACTION_VIEW);
      onIntent(intent);
      return;
    }
    finish();
  }
}