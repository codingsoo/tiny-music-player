package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  private static final List<String> AUDIO_EXTENSIONS = Arrays.asList(
    ".mp3", ".wav", ".ogg", ".flac", ".m4a", ".aac", ".wma", ".opus", ".mid", ".midi"
  );

  final HWListener hwListener;
  final Notifications notifications;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  /**
   * URI of the currently playing audio file
   */
  private Uri currentAudioUri;
  /**
   * random number generator for shuffle mode
   */
  private final Random random;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
    random = new Random();
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
      var isPlaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      var isShuffling = audioPlayer.isShuffling();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !isShuffling);
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
      /* stop previous audio player if running */
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }

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
    audioPlayer.setState(playing, looping, shuffling);
    hwListener.setState(playing, looping, shuffling);
    notifications.setState(playing, looping, shuffling);
  }

  /**
   * Select and play a random audio file from the same directory as the current track.
   * Called by AudioPlayer.onCompletion when shuffle mode is enabled.
   */
  void playNextShuffledTrack() {
    if (currentAudioUri == null) {
      stopSelf();
      return;
    }

    String currentPath = currentAudioUri.getPath();
    if (currentPath == null) {
      stopSelf();
      return;
    }

    File currentFile = new File(currentPath);
    File parentDirectory = currentFile.getParentFile();

    if (parentDirectory == null || !parentDirectory.isDirectory()) {
      stopSelf();
      return;
    }

    File[] allFiles = parentDirectory.listFiles();
    if (allFiles == null || allFiles.length == 0) {
      stopSelf();
      return;
    }

    List<File> audioFiles = new ArrayList<>();
    for (File file : allFiles) {
      if (file.isFile() && isAudioFile(file.getName())) {
        audioFiles.add(file);
      }
    }

    if (audioFiles.isEmpty()) {
      stopSelf();
      return;
    }

    /* avoid replaying the same track when possible */
    if (audioFiles.size() > 1) {
      audioFiles.remove(currentFile);
    }

    if (audioFiles.isEmpty()) {
      stopSelf();
      return;
    }

    File nextTrack = audioFiles.get(random.nextInt(audioFiles.size()));
    Uri nextUri = Uri.fromFile(nextTrack);

    boolean wasShuffling = audioPlayer.isShuffling();
    setAudio(nextUri);
    if (wasShuffling) {
      setState(true, false, true);
    }
  }

  /**
   * Check if a filename has a recognized audio file extension
   */
  private boolean isAudioFile(String fileName) {
    String lowerName = fileName.toLowerCase(Locale.ROOT);
    for (String extension : AUDIO_EXTENSIONS) {
      if (lowerName.endsWith(extension)) {
        return true;
      }
    }
    return false;
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
