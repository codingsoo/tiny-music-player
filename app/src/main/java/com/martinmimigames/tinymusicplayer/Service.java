package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.File;
import java.io.FilenameFilter;
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

  private boolean shuffleEnabled;
  private ArrayList<Uri> trackList;
  private int currentTrackIndex;

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
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping);
        case Launcher.PLAY -> setState(true, isLooping);
        case Launcher.PAUSE -> setState(false, isLooping);
        case Launcher.LOOP -> setState(isPLaying, !isLooping);
        case Launcher.SHUFFLE -> {
          shuffleEnabled = !shuffleEnabled;
          notifications.setState(isPLaying, isLooping, shuffleEnabled);
        }
        case Launcher.SKIP_NEXT -> {
          if (shuffleEnabled && trackList != null && trackList.size() > 1) playNextShuffleTrack();
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
    discoverSiblingTracks(audioLocation);
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
  void setState(boolean playing, boolean looping) {
    audioPlayer.setState(playing, looping);
    hwListener.setState(playing, looping);
    notifications.setState(playing, looping, shuffleEnabled);
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

  private void discoverSiblingTracks(Uri audioLocation) {
    trackList = new ArrayList<>();
    currentTrackIndex = 0;

    try {
      var file = new File(audioLocation.getPath());
      var parentDir = file.getParentFile();

      if (parentDir != null && parentDir.isDirectory()) {
        var audioFiles = parentDir.listFiles(new FilenameFilter() {
          @Override
          public boolean accept(File dir, String name) {
            var lower = name.toLowerCase();
            return lower.endsWith(".mp3") || lower.endsWith(".wav") ||
              lower.endsWith(".ogg") || lower.endsWith(".flac") ||
              lower.endsWith(".aac") || lower.endsWith(".m4a") ||
              lower.endsWith(".wma") || lower.endsWith(".opus") ||
              lower.endsWith(".mid") || lower.endsWith(".midi");
          }
        });

        if (audioFiles != null && audioFiles.length > 0) {
          for (var audioFile : audioFiles) {
            trackList.add(Uri.fromFile(audioFile));
          }
          // Find the index of the currently playing file
          var currentFileName = file.getName();
          for (int i = 0; i < trackList.size(); i++) {
            if (new File(trackList.get(i).getPath()).getName().equals(currentFileName)) {
              currentTrackIndex = i;
              break;
            }
          }
          return;
        }
      }
    } catch (Exception e) {
      // Fall through to single-track fallback
    }

    // Fallback: just use the single URI
    trackList.add(audioLocation);
    currentTrackIndex = 0;
  }

  void playNextShuffleTrack() {
    if (trackList == null || trackList.size() <= 1) {
      stopSelf();
      return;
    }

    var random = new Random();
    int nextIndex;
    do {
      nextIndex = random.nextInt(trackList.size());
    } while (nextIndex == currentTrackIndex);

    currentTrackIndex = nextIndex;
    var nextUri = trackList.get(currentTrackIndex);

    try {
      if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

      audioPlayer = new AudioPlayer(this, nextUri);
      audioPlayer.start();

      notifications.updateTitle(new File(nextUri.getPath()).getName());
      notifications.setState(true, false, shuffleEnabled);
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

  void onTrackCompleted() {
    if (shuffleEnabled && trackList != null && trackList.size() > 1) {
      playNextShuffleTrack();
    } else {
      stopSelf();
    }
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
