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
  final PlaylistManager playlistManager;
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
    playlistManager = new PlaylistManager(this);
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
      var isPlaying = audioPlayer != null && audioPlayer.isPlaying();
      var isLooping = audioPlayer != null && audioPlayer.isLooping();
      var isShuffling = playlistManager.isShuffleEnabled();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(audioPlayer != null ? !isPlaying : false, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !isShuffling);
        case Launcher.SKIP_NEXT -> skipToNext();
        case Launcher.SKIP_PREV -> skipToPrevious();
        /* cancel audio playback and kill service */
        case Launcher.KILL -> stopSelf();
      }
    } else {
      switch (intent.getAction()) {
        case Intent.ACTION_VIEW -> initializeAndPlay(intent.getData());
        case Intent.ACTION_SEND -> initializeAndPlay(intent.getParcelableExtra(Intent.EXTRA_STREAM));
      }
    }
  }

  /**
   * Initialize playlist and start playing
   */
  void initializeAndPlay(final Uri audioLocation) {
    playlistManager.initializePlaylist(audioLocation);
    setAudio(playlistManager.getCurrentTrack());
  }

  void setAudio(final Uri audioLocation) {
    try {
      // Stop current playback if any
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }

      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(audioLocation, playlistManager.hasMultipleTracks());

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
   * Skip to the next track
   */
  void skipToNext() {
    Uri nextTrack = playlistManager.getNextTrack();
    if (nextTrack != null) {
      setAudio(nextTrack);
    }
  }

  /**
   * Skip to the previous track
   */
  void skipToPrevious() {
    Uri prevTrack = playlistManager.getPreviousTrack();
    if (prevTrack != null) {
      setAudio(prevTrack);
    }
  }

  /**
   * Called when current track completes
   */
  void onTrackComplete() {
    if (playlistManager.isShuffleEnabled() || playlistManager.hasMultipleTracks()) {
      // In shuffle mode or with multiple tracks, play next
      skipToNext();
    } else {
      // Single track, no shuffle - stop
      stopSelf();
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    playlistManager.setShuffleEnabled(shuffling);
    if (audioPlayer != null) {
      audioPlayer.setState(playing, looping);
    }
    hwListener.setState(playing, looping);
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
