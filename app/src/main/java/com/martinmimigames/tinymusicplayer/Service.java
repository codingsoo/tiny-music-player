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
      var isPLaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      var isShuffling = playlist.isShuffleEnabled();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPLaying, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPLaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPLaying, isLooping, !isShuffling);
        case Launcher.NEXT -> playNextTrack();
        case Launcher.PREV -> playPreviousTrack();
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
    // Initialize playlist from the selected audio file
    playlist.initFromUri(this, audioLocation);
    playCurrentTrack();
  }

  /**
   * Play the current track in the playlist
   */
  void playCurrentTrack() {
    Uri currentTrack = playlist.getCurrentTrack();
    if (currentTrack == null) {
      stopSelf();
      return;
    }
    
    try {
      // Stop existing player if any
      if (audioPlayer != null && !audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }
      
      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, currentTrack);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(currentTrack, playlist.hasMultipleTracks());

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
   * Play the next track in the playlist
   */
  void playNextTrack() {
    Uri nextTrack = playlist.getNextTrack();
    if (nextTrack != null) {
      playCurrentTrack();
    } else {
      stopSelf();
    }
  }

  /**
   * Play the previous track in the playlist
   */
  void playPreviousTrack() {
    Uri prevTrack = playlist.getPreviousTrack();
    if (prevTrack != null) {
      playCurrentTrack();
    } else {
      stopSelf();
    }
  }

  /**
   * Called when the current track completes
   */
  void onTrackComplete() {
    if (audioPlayer.isLooping()) {
      // Looping is handled by MediaPlayer, but if we get here, restart
      playCurrentTrack();
    } else if (playlist.hasMultipleTracks()) {
      // Play next track (random if shuffle is enabled)
      playNextTrack();
    } else {
      // Single track, no loop - stop
      stopSelf();
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    playlist.setShuffleEnabled(shuffling);
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
