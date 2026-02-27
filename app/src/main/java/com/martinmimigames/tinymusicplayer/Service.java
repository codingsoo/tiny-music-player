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
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  private boolean shuffling;
  private final Random random;

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
      var isPlaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping, shuffling);
        case Launcher.PLAY -> setState(true, isLooping, shuffling);
        case Launcher.PAUSE -> setState(false, isLooping, shuffling);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, shuffling);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !shuffling);
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
   * Called by AudioPlayer when media is prepared and ready to play
   */
  void onPlayerReady() {
    setState(true, false, shuffling);
  }

  /**
   * Skip to the next random track when shuffle is active
   */
  void skipToNext() {
    if (shuffling) {
      playRandomTrack();
    }
  }

  /**
   * Called by AudioPlayer when the current track finishes playing
   */
  void onTrackCompleted() {
    if (shuffling) {
      playRandomTrack();
    } else {
      stopSelf();
    }
  }

  /**
   * Select and play a random audio track from the device
   */
  private void playRandomTrack() {
    Uri randomTrackUri = getRandomAudioUri();
    if (randomTrackUri != null) {
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }
      setAudio(randomTrackUri);
    } else {
      stopSelf();
    }
  }

  /**
   * Query MediaStore for all audio files and return a random one
   */
  private Uri getRandomAudioUri() {
    ArrayList<Uri> audioUris = new ArrayList<>();
    ContentResolver contentResolver = getContentResolver();
    Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    String[] projection = {MediaStore.Audio.Media._ID};

    try (Cursor cursor = contentResolver.query(collection, projection, null, null, null)) {
      if (cursor != null) {
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        while (cursor.moveToNext()) {
          long id = cursor.getLong(idColumn);
          Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
          audioUris.add(contentUri);
        }
      }
    } catch (Exception e) {
      return null;
    }

    if (audioUris.isEmpty()) {
      return null;
    }

    int randomIndex = random.nextInt(audioUris.size());
    return audioUris.get(randomIndex);
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
