package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  private static final String[] AUDIO_EXTENSIONS = {".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac", ".wma", ".opus"};

  final HWListener hwListener;
  final Notifications notifications;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;

  private final Handler mainHandler = new Handler(Looper.getMainLooper());
  private final Random random = new Random();
  private boolean shuffling = false;
  private boolean looping = false;
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
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, shuffling);
        case Launcher.PLAY -> setState(true, isLooping, shuffling);
        case Launcher.PAUSE -> setState(false, isLooping, shuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, shuffling);
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !shuffling);
        case Launcher.NEXT -> {
          if (shuffling) {
            playNextRandom();
          }
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
      /* interrupt old audio player if it exists */
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }

      /* store current audio location */
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
   * Called by AudioPlayer after prepare succeeds (from AudioPlayer thread).
   * Posts state update to main thread.
   */
  void onAudioReady() {
    mainHandler.post(() -> setState(true, looping, shuffling));
  }

  /**
   * Called by AudioPlayer on track completion (from AudioPlayer thread).
   * Posts next action to main thread.
   */
  void onTrackCompleted() {
    mainHandler.post(() -> {
      if (shuffling) {
        playNextRandom();
      } else {
        stopSelf();
      }
    });
  }

  /**
   * Pick a random sibling audio file and play it, or stop if none found.
   */
  void playNextRandom() {
    var nextUri = pickRandomSibling();
    if (nextUri != null) {
      setAudio(nextUri);
    } else {
      stopSelf();
    }
  }

  /**
   * Pick a random audio file from the same directory as the current track.
   *
   * @return Uri of a random sibling audio file, or null if none found
   */
  Uri pickRandomSibling() {
    if (currentAudioLocation == null) return null;

    var path = currentAudioLocation.getPath();
    if (path == null) return null;

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return null;

    var files = parentDir.listFiles();
    if (files == null) return null;

    /* filter to audio files only */
    var audioFiles = new java.util.ArrayList<File>();
    for (var file : files) {
      if (file.isFile()) {
        var name = file.getName().toLowerCase(Locale.ROOT);
        for (var ext : AUDIO_EXTENSIONS) {
          if (name.endsWith(ext)) {
            audioFiles.add(file);
            break;
          }
        }
      }
    }

    if (audioFiles.isEmpty()) return null;

    if (audioFiles.size() == 1) {
      /* only one audio file, replay it */
      return Uri.fromFile(audioFiles.get(0));
    }

    /* rejection sampling: pick a random file that's not the current one */
    var currentName = currentFile.getName();
    File picked;
    do {
      picked = audioFiles.get(random.nextInt(audioFiles.size()));
    } while (picked.getName().equals(currentName));

    return Uri.fromFile(picked);
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    this.looping = looping;
    this.shuffling = shuffling;
    audioPlayer.setState(playing, looping);
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
    if (audioPlayer != null && !audioPlayer.isInterrupted()) audioPlayer.interrupt();

    super.onDestroy();
  }
}
