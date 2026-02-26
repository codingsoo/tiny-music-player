package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  private static final String[] AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac",
    ".wma", ".mp4", ".3gp", ".mkv", ".webm", ".opus"
  };

  final HWListener hwListener;
  final Notifications notifications;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  private boolean shuffling = false;
  private Uri currentAudioUri;

  /**
   * check if shuffle mode is enabled
   */
  boolean isShuffling() {
    return shuffling;
  }

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
        case Launcher.SKIP -> {
          if (shuffling) playNextRandom();
        }
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
      currentAudioUri = audioLocation;

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
   * Called when the current track finishes playing
   */
  void onTrackCompleted() {
    if (shuffling) {
      playNextRandom();
    } else {
      stopSelf();
    }
  }

  /**
   * Pick a random audio/video file from the same directory and play it
   */
  void playNextRandom() {
    try {
      if (currentAudioUri == null) {
        stopSelf();
        return;
      }

      var path = currentAudioUri.getPath();
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

      var audioFiles = parentDir.listFiles(new FilenameFilter() {
        public boolean accept(File dir, String name) {
          var lower = name.toLowerCase();
          for (var ext : AUDIO_EXTENSIONS) {
            if (lower.endsWith(ext)) return true;
          }
          return false;
        }
      });

      if (audioFiles == null || audioFiles.length == 0) {
        stopSelf();
        return;
      }

      File selected;
      if (audioFiles.length == 1) {
        selected = audioFiles[0];
      } else {
        var random = new Random();
        var attempts = 0;
        do {
          selected = audioFiles[random.nextInt(audioFiles.length)];
          attempts++;
        } while (selected.getAbsolutePath().equals(currentFile.getAbsolutePath()) && attempts < audioFiles.length * 2);
      }

      /* clean up current player before starting new track */
      if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

      setAudio(Uri.fromFile(selected));
    } catch (Exception e) {
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
