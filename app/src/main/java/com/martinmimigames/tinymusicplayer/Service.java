package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

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
  boolean shuffling = false;
  private Uri currentAudioUri;

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
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !shuffling);
        case Launcher.SKIP_NEXT -> skipToNext();
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

  void setAudio(final Uri audioLocation) {
    currentAudioUri = audioLocation;
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
    this.shuffling = shuffling;
    audioPlayer.setState(playing, looping, shuffling);
    hwListener.setState(playing, looping, shuffling);
    notifications.setState(playing, looping, shuffling);
  }

  /**
   * Discover audio files in the same directory as the current file
   * and return a random one (excluding the current file).
   */
  private Uri getRandomSiblingAudio() {
    if (currentAudioUri == null) return null;

    String path = currentAudioUri.getPath();
    if (path == null) return null;

    java.io.File currentFile = new java.io.File(path);
    java.io.File parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return null;

    java.io.File[] files = parentDir.listFiles();
    if (files == null) return null;

    String[] audioExtensions = {".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a", ".wma", ".opus", ".mid", ".midi", ".3gp", ".amr"};
    java.util.ArrayList<java.io.File> audioFiles = new java.util.ArrayList<>();

    for (java.io.File file : files) {
      if (file.isFile() && !file.equals(currentFile)) {
        String name = file.getName().toLowerCase();
        for (String ext : audioExtensions) {
          if (name.endsWith(ext)) {
            audioFiles.add(file);
            break;
          }
        }
      }
    }

    if (audioFiles.isEmpty()) return null;

    java.io.File randomFile = audioFiles.get(new java.util.Random().nextInt(audioFiles.size()));
    return Uri.fromFile(randomFile);
  }

  /**
   * Skip to next random audio file
   */
  void skipToNext() {
    Uri nextAudio = getRandomSiblingAudio();
    if (nextAudio != null) {
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }
      setAudio(nextAudio);
    } else {
      Exceptions.throwError(this, "No other audio files found in directory");
    }
  }

  /**
   * Called when a track finishes playing
   */
  void onTrackCompleted() {
    if (shuffling) {
      skipToNext();
    } else {
      stopSelf();
    }
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
