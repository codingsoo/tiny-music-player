package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  final HWListener hwListener;
  final Notifications notifications;
  boolean shuffling = false;
  Uri currentAudioLocation;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;

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
    this.currentAudioLocation = audioLocation;
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
   * Play a random audio file from the same directory as the current track
   */
  void playRandomTrack() {
    if (currentAudioLocation == null) {
      stopSelf();
      return;
    }
    var currentFile = new File(currentAudioLocation.getPath());
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) {
      stopSelf();
      return;
    }
    var audioFiles = parentDir.listFiles((dir, name) -> {
      var lowerName = name.toLowerCase();
      return lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") || lowerName.endsWith(".ogg")
        || lowerName.endsWith(".flac") || lowerName.endsWith(".aac") || lowerName.endsWith(".m4a")
        || lowerName.endsWith(".wma") || lowerName.endsWith(".opus") || lowerName.endsWith(".mid")
        || lowerName.endsWith(".midi") || lowerName.endsWith(".3gp") || lowerName.endsWith(".mp4");
    });
    if (audioFiles == null || audioFiles.length == 0) {
      stopSelf();
      return;
    }
    var random = new java.util.Random();
    var randomFile = audioFiles[random.nextInt(audioFiles.length)];
    var wasShuffling = shuffling;
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();
    setAudio(Uri.fromFile(randomFile));
    if (audioPlayer != null) {
      setState(true, false, wasShuffling);
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
