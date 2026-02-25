package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  final HWListener hwListener;
  final Notifications notifications;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  private boolean shuffling = false;
  Uri currentUri;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
  }

  /**
   * unused
   */
  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  /**
   * setup
   */
  @Override
  public void onCreate() {
    hwListener.create();
    notifications.create();

    super.onCreate();
  }

  /**
   * startup logic
   */
  @Override
  public void onStart(final Intent intent, final int startId) {
    /* check if called from self */
    if (intent.getAction() == null) {
      var isPLaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, shuffling);
        case Launcher.PLAY -> setState(true, isLooping, shuffling);
        case Launcher.PAUSE -> setState(false, isLooping, shuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, shuffling);
        case Launcher.SHUFFLE -> { shuffling = !shuffling; setState(isPLaying, isLooping, shuffling); }
        case Launcher.SKIP_NEXT -> playNext();
        /* cancel audio playback and kill service */
        case Launcher.KILL -> stopSelf();
      }
    } else {
      switch (intent.getAction()) {
        case Intent.ACTION_VIEW -> setAudio(intent.getData());
        case Intent.ACTION_SEND -> setAudio(intent.getParcelableExtra(Intent.EXTRA_STREAM));
      }
    }
  }

  boolean isShuffling() {
    return shuffling;
  }

  void playNext() {
    if (currentUri == null) {
      stopSelf();
      return;
    }

    var filePath = getFilePath(currentUri);
    if (filePath == null) {
      stopSelf();
      return;
    }

    var currentFile = new File(filePath);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) {
      stopSelf();
      return;
    }

    var audioFiles = parentDir.listFiles(file -> file.isFile() && isAudioFile(file));
    if (audioFiles == null || audioFiles.length == 0) {
      stopSelf();
      return;
    }

    var randomFile = audioFiles[new Random().nextInt(audioFiles.length)];
    var newUri = Uri.fromFile(randomFile);

    /* clean up current audio player before starting new one */
    if (audioPlayer != null && !audioPlayer.isInterrupted()) {
      audioPlayer.interrupt();
    }

    setAudio(newUri);
  }

  private String getFilePath(Uri uri) {
    if ("file".equals(uri.getScheme())) {
      return uri.getPath();
    }
    if ("content".equals(uri.getScheme())) {
      try (var cursor = getContentResolver().query(uri, new String[]{"_data"}, null, null, null)) {
        if (cursor != null && cursor.moveToFirst()) {
          var path = cursor.getString(0);
          if (path != null) return path;
        }
      } catch (Exception e) {
        /* fall through */
      }
    }
    /* fallback: try getPath() directly */
    return uri.getPath();
  }

  private boolean isAudioFile(File file) {
    var name = file.getName().toLowerCase();
    return name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg")
        || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac")
        || name.endsWith(".wma") || name.endsWith(".opus") || name.endsWith(".mid")
        || name.endsWith(".midi") || name.endsWith(".3gp") || name.endsWith(".amr");
  }

  void setAudio(final Uri audioLocation) {
    this.currentUri = audioLocation;
    try {
      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(audioLocation);

      /* start service as foreground */
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
        startForeground(Notifications.NOTIFICATION_ID, notifications.notification);

    } catch (IllegalArgumentException e) {
      Exceptions.throwError(this, Exceptions.IllegalArgument);
    } catch (SecurityException e) {
      Exceptions.throwError(this, Exceptions.Security);
    } catch (IllegalStateException e) {
      Exceptions.throwError(this, Exceptions.IllegalState);
    } catch (IOException e) {
      Exceptions.throwError(this, Exceptions.IO);
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    audioPlayer.setState(playing, looping, shuffling);
    hwListener.setState(playing, looping, shuffling);
    notifications.setState(playing, looping, shuffling);
  }

  /**
   * forward to startup logic for newer androids
   */
  @TargetApi(Build.VERSION_CODES.ECLAIR)
  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    onStart(intent, startId);
    return START_STICKY;
  }

  /**
   * service killing logic
   */
  @Override
  public void onDestroy() {
    notifications.destroy();
    hwListener.destroy();
    /* interrupt audio playback logic */
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

    super.onDestroy();
  }
}
