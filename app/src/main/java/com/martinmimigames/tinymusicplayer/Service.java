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

  final HWListener hwListener;
  final Notifications notifications;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;

  private Uri currentAudioUri;
  private boolean shuffling = false;

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
      if (audioPlayer == null) return;
      var isPLaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, shuffling);
        case Launcher.PLAY -> setState(true, isLooping, shuffling);
        case Launcher.PAUSE -> setState(false, isLooping, shuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, shuffling);
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !shuffling);
        case Launcher.SKIP -> playNextRandom();
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
      this.currentAudioUri = audioLocation;

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
   * check if shuffle mode is active
   */
  boolean isShuffling() {
    return shuffling;
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
   * If shuffling, plays the next random track; otherwise stops.
   */
  void onTrackCompleted() {
    if (shuffling) {
      playNextRandom();
    } else {
      stopSelf();
    }
  }

  /**
   * Scan the parent directory for sibling audio files and return a random one.
   */
  private Uri getRandomSiblingAudio() {
    if (currentAudioUri == null) return null;
    String path = currentAudioUri.getPath();
    if (path == null) return null;
    File currentFile = new File(path);
    File parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return null;

    String[] audioExtensions = {".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a", ".wma", ".opus", ".mid", ".midi"};
    File[] files = parentDir.listFiles();
    if (files == null) return null;

    var audioFiles = new ArrayList<File>();
    for (File f : files) {
      if (f.isFile() && !f.equals(currentFile)) {
        String name = f.getName().toLowerCase();
        for (String ext : audioExtensions) {
          if (name.endsWith(ext)) {
            audioFiles.add(f);
            break;
          }
        }
      }
    }

    if (audioFiles.isEmpty()) return null;
    return Uri.fromFile(audioFiles.get(new Random().nextInt(audioFiles.size())));
  }

  /**
   * Play a random sibling audio file, or stop if none found.
   */
  void playNextRandom() {
    Uri next = getRandomSiblingAudio();
    if (next != null) {
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }
      setAudio(next);
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
    if (audioPlayer != null && !audioPlayer.isInterrupted()) audioPlayer.interrupt();

    super.onDestroy();
  }
}
