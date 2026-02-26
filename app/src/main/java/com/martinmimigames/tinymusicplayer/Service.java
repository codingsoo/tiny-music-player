package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
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
  private Uri currentAudioLocation;
  private boolean shuffling = false;
  private final Random random = new Random();

  private static final String[] AUDIO_EXTENSIONS = {
    "mp3", "wav", "ogg", "flac", "m4a", "aac", "wma", "opus",
    "mid", "midi", "amr", "3gp", "3gpp"
  };

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
    try {
      currentAudioLocation = audioLocation;
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
   * check if shuffle is enabled
   */
  boolean isShuffling() {
    return shuffling;
  }

  /**
   * called when a track finishes playing
   */
  void onTrackCompleted() {
    if (shuffling) {
      playRandomTrack();
    } else {
      stopSelf();
    }
  }

  /**
   * pick a random audio file from the same directory and play it
   */
  private void playRandomTrack() {
    if (currentAudioLocation == null) {
      stopSelf();
      return;
    }

    var path = currentAudioLocation.getPath();
    if (path == null) {
      stopSelf();
      return;
    }

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) {
      stopSelf();
      return;
    }

    var files = parentDir.listFiles();
    if (files == null) {
      stopSelf();
      return;
    }

    var candidates = new ArrayList<File>();
    for (var file : files) {
      if (!file.isFile()) continue;
      if (file.equals(currentFile)) continue;
      var name = file.getName();
      var dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) continue;
      var ext = name.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
      var isAudio = false;
      for (var audioExt : AUDIO_EXTENSIONS) {
        if (audioExt.equals(ext)) {
          isAudio = true;
          break;
        }
      }
      if (isAudio) {
        candidates.add(file);
      }
    }

    if (candidates.isEmpty()) {
      stopSelf();
      return;
    }

    var chosen = candidates.get(random.nextInt(candidates.size()));
    setAudio(Uri.fromFile(chosen));
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
