package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

/**
 * service for playing music
 */
public class Service extends android.app.Service {

  final HWListener hwListener;
  final Notifications notifications;
  final Playlist playlist;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
    playlist = new Playlist();
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
      var isShuffling = playlist.isShuffleEnabled();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !isShuffling);
        case Launcher.NEXT -> playNextTrack();
        case Launcher.PREVIOUS -> playPreviousTrack();
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
      /* set the track in the playlist */
      playlist.setTrack(audioLocation);

      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(audioLocation, playlist.isShuffleEnabled());

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
   * Called when the current track completes playback.
   * If shuffle is enabled, plays the next random track.
   * Otherwise, stops the service (unless looping is handled by MediaPlayer).
   */
  void onTrackCompleted() {
    if (playlist.isShuffleEnabled()) {
      playNextTrack();
    } else {
      stopSelf();
    }
  }

  /**
   * Play the next track in the playlist.
   * In shuffle mode, this will be a random track.
   */
  void playNextTrack() {
    Uri nextTrack = playlist.getNextTrack();
    if (nextTrack != null) {
      try {
        audioPlayer.changeTrack(nextTrack);
        notifications.getNotification(nextTrack, playlist.isShuffleEnabled());
        notifications.setState(true, audioPlayer.isLooping(), playlist.isShuffleEnabled());
      } catch (Exception e) {
        Exceptions.throwError(this, Exceptions.IO);
        stopSelf();
      }
    } else {
      stopSelf();
    }
  }

  /**
   * Play the previous track in the playlist.
   * In shuffle mode, this will be a random track.
   */
  void playPreviousTrack() {
    Uri prevTrack = playlist.getPreviousTrack();
    if (prevTrack != null) {
      try {
        audioPlayer.changeTrack(prevTrack);
        notifications.getNotification(prevTrack, playlist.isShuffleEnabled());
        notifications.setState(true, audioPlayer.isLooping(), playlist.isShuffleEnabled());
      } catch (Exception e) {
        Exceptions.throwError(this, Exceptions.IO);
        stopSelf();
      }
    } else {
      stopSelf();
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    audioPlayer.setState(playing, looping);
    playlist.setShuffleEnabled(shuffling);
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
