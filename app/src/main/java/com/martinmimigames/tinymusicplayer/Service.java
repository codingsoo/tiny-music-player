package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

  private boolean shuffling;
  private Uri currentAudioLocation;
  private List<Uri> trackList;
  private final Random random = new Random();

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
        case Launcher.SKIP_NEXT -> playNext();
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
      /* stop any existing playback */
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }

      currentAudioLocation = audioLocation;
      discoverTracks(audioLocation);

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
   * Discover audio files in the same directory as the given URI
   */
  private void discoverTracks(Uri audioLocation) {
    trackList = new ArrayList<>();
    trackList.add(audioLocation);

    try {
      String path = null;
      /* try to get the file path from the URI */
      if ("file".equals(audioLocation.getScheme())) {
        path = audioLocation.getPath();
      } else {
        /* try to resolve content:// URI to file path */
        ContentResolver resolver = getContentResolver();
        String[] projection = {MediaStore.Audio.Media.DATA};
        Cursor cursor = resolver.query(audioLocation, projection, null, null, null);
        if (cursor != null) {
          try {
            if (cursor.moveToFirst()) {
              path = cursor.getString(0);
            }
          } finally {
            cursor.close();
          }
        }
      }

      if (path == null) return;

      /* get the parent directory */
      File parentDir = new File(path).getParentFile();
      if (parentDir == null || !parentDir.exists()) return;

      /* find all audio files in the same directory */
      File[] files = parentDir.listFiles();
      if (files == null) return;

      trackList.clear();
      for (File file : files) {
        if (file.isFile() && isAudioFile(file.getName())) {
          trackList.add(Uri.fromFile(file));
        }
      }

      /* if we found nothing, fall back to just the current track */
      if (trackList.isEmpty()) {
        trackList.add(audioLocation);
      }
    } catch (Exception e) {
      /* on any error, keep the single-track list */
      if (trackList.isEmpty()) {
        trackList.add(audioLocation);
      }
    }
  }

  /**
   * Check if a filename has a common audio file extension
   */
  private static boolean isAudioFile(String name) {
    String lower = name.toLowerCase();
    return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg")
      || lower.endsWith(".flac") || lower.endsWith(".aac") || lower.endsWith(".m4a")
      || lower.endsWith(".wma") || lower.endsWith(".opus") || lower.endsWith(".mid")
      || lower.endsWith(".midi") || lower.endsWith(".3gp") || lower.endsWith(".mp4");
  }

  /**
   * Play the next random track from the discovered track list
   */
  void playNext() {
    if (trackList == null || trackList.isEmpty()) {
      stopSelf();
      return;
    }

    Uri nextTrack;
    if (trackList.size() == 1) {
      nextTrack = trackList.get(0);
    } else {
      /* pick a random track, avoiding the current one if possible */
      Uri candidate;
      int attempts = 0;
      do {
        candidate = trackList.get(random.nextInt(trackList.size()));
        attempts++;
      } while (candidate.equals(currentAudioLocation) && attempts < 10);
      nextTrack = candidate;
    }

    setAudio(nextTrack);
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
