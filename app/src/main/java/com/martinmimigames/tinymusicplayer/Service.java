package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
  private Uri currentAudioLocation;

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
      var isShuffling = audioPlayer.isShuffling();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !isShuffling);
        case Launcher.SKIP -> playNextShuffleTrack();
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
    try {
      this.currentAudioLocation = audioLocation;
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
  void playNextShuffleTrack() {
    if (currentAudioLocation == null) {
      stopSelf();
      return;
    }

    var currentFile = new File(currentAudioLocation.getPath());
    var parentDir = currentFile.getParentFile();

    if (parentDir == null || !parentDir.exists()) {
      stopSelf();
      return;
    }

    var audioExtensions = Arrays.asList(
      "mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus",
      "mid", "midi", "amr", "3gp", "mp4", "mkv", "webm"
    );

    var audioFiles = parentDir.listFiles(file -> {
      if (!file.isFile()) return false;
      var name = file.getName().toLowerCase();
      var dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) return false;
      var ext = name.substring(dotIndex + 1);
      return audioExtensions.contains(ext);
    });

    if (audioFiles == null || audioFiles.length == 0) {
      stopSelf();
      return;
    }

    var random = new Random();
    var nextFile = audioFiles[random.nextInt(audioFiles.length)];
    var nextUri = Uri.fromFile(nextFile);

    // Stop current playback
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

    // Remember shuffle state before setting new audio
    var wasShuffling = shuffling;
    setAudio(nextUri);
    // Restore shuffle state after new audio is set up
    if (wasShuffling && audioPlayer != null) {
      setState(true, false, true);
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
