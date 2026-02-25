package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  private static final String[] AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".aac",
    ".m4a", ".wma", ".opus", ".mid", ".midi"
  };

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
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, shuffling);
        case Launcher.PLAY -> setState(true, isLooping, shuffling);
        case Launcher.PAUSE -> setState(false, isLooping, shuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, shuffling);
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !shuffling);
        case Launcher.SKIP -> onSkipNext();
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
   * Called when a track finishes playing.
   * If shuffle is enabled, plays a random sibling audio file;
   * otherwise stops the service.
   */
  void onTrackCompleted() {
    if (shuffling) {
      var next = getRandomSiblingAudio();
      if (next != null) {
        setAudio(next);
        return;
      }
    }
    stopSelf();
  }

  /**
   * Skip to the next random track when shuffle is enabled.
   */
  private void onSkipNext() {
    if (shuffling) {
      var next = getRandomSiblingAudio();
      if (next != null) {
        setAudio(next);
      }
    }
  }

  /**
   * Discover sibling audio files in the same directory as the current track
   * and return a random one, excluding the current file.
   *
   * @return a Uri for a random sibling audio file, or null if none found
   */
  private Uri getRandomSiblingAudio() {
    if (currentAudioLocation == null) return null;

    var path = currentAudioLocation.getPath();
    if (path == null) return null;

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.exists()) return null;

    var files = parentDir.listFiles();
    if (files == null) return null;

    var siblings = new ArrayList<File>();
    for (var file : files) {
      if (!file.isFile()) continue;
      if (file.equals(currentFile)) continue;
      var name = file.getName().toLowerCase();
      for (var ext : AUDIO_EXTENSIONS) {
        if (name.endsWith(ext)) {
          siblings.add(file);
          break;
        }
      }
    }

    if (siblings.isEmpty()) return null;

    var random = new Random();
    var selected = siblings.get(random.nextInt(siblings.size()));
    return Uri.fromFile(selected);
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
