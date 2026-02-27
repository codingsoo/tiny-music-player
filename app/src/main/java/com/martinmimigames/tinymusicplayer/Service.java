package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  final HWListener hwListener;
  final Notifications notifications;
  private final Random random;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  private Uri currentAudioLocation;
  private boolean shuffling;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
    random = new Random();
    shuffling = false;
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
        /* toggle shuffle mode */
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !shuffling);
        /* skip to next random track when shuffling */
        case Launcher.SKIP_NEXT -> skipToNext();
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

      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }

      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation, shuffling);
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
   * skip to next random track if shuffle is enabled
   */
  void skipToNext() {
    if (shuffling) {
      playNextShuffledTrack();
    }
  }

  /**
   * play a random track from the device media library
   */
  void playNextShuffledTrack() {
    var randomTrackUri = getRandomAudioUri();
    if (randomTrackUri != null) {
      setAudio(randomTrackUri);
    } else {
      stopSelf();
    }
  }

  /**
   * query the device media store for a random audio track
   */
  private Uri getRandomAudioUri() {
    var audioUris = new ArrayList<Uri>();
    ContentResolver contentResolver = getContentResolver();

    try (Cursor cursor = contentResolver.query(
      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
      new String[]{MediaStore.Audio.Media._ID},
      MediaStore.Audio.Media.IS_MUSIC + " != 0",
      null,
      null)) {

      if (cursor != null && cursor.moveToFirst()) {
        var idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        do {
          var audioId = cursor.getLong(idColumnIndex);
          var audioUri = Uri.withAppendedPath(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            String.valueOf(audioId));
          audioUris.add(audioUri);
        } while (cursor.moveToNext());
      }
    } catch (Exception e) {
      return null;
    }

    if (audioUris.isEmpty()) {
      return null;
    }

    /* pick a random track, avoiding the current one if possible */
    if (audioUris.size() > 1 && currentAudioLocation != null) {
      audioUris.remove(currentAudioLocation);
    }

    return audioUris.get(random.nextInt(audioUris.size()));
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
