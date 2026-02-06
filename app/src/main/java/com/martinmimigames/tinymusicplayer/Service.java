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
  /**
   * audio playing logic class
   */
  private AudioPlayer audioPlayer;
  /**
   * playlist manager
   */
  private Playlist playlist;

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
      var isPlaying = audioPlayer != null && audioPlayer.isPlaying();
      var isLooping = audioPlayer != null && audioPlayer.isLooping();
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
      // Initialize playlist with the audio file and discover siblings
      playlist.initialize(this, audioLocation);
      
      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(audioLocation, playlist.getTrackCount() > 1);

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
    if (nextTrack != null && audioPlayer != null) {
      if (audioPlayer.playNewTrack(nextTrack)) {
        notifications.updateTrackInfo(nextTrack);
        setState(true, audioPlayer.isLooping(), playlist.isShuffleEnabled());
      }
    }
  }

  /**
   * Play the previous track in the playlist
   */
  void playPreviousTrack() {
    Uri prevTrack = playlist.getPreviousTrack();
    if (prevTrack != null && audioPlayer != null) {
      if (audioPlayer.playNewTrack(prevTrack)) {
        notifications.updateTrackInfo(prevTrack);
        setState(true, audioPlayer.isLooping(), playlist.isShuffleEnabled());
      }
    }
  }

  /**
   * Called when a track completes playback
   */
  void onTrackCompleted() {
    boolean isLooping = audioPlayer != null && audioPlayer.isLooping();
    boolean isShuffling = playlist.isShuffleEnabled();
    
    if (isLooping) {
      // Loop the current track
      Uri currentTrack = playlist.getCurrentTrack();
      if (currentTrack != null && audioPlayer != null) {
        audioPlayer.playNewTrack(currentTrack);
        setState(true, true, isShuffling);
      }
    } else if (isShuffling || playlist.getTrackCount() > 1) {
      // Play next track (random if shuffle enabled)
      if (!isShuffling && playlist.isLastTrack()) {
        // End of playlist in sequential mode without loop
        stopSelf();
      } else {
        playNextTrack();
      }
    } else {
      // Single track, no loop - stop
      stopSelf();
    }
  }

  /**
   * Check if shuffle is enabled
   */
  boolean isShuffleEnabled() {
    return playlist.isShuffleEnabled();
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    playlist.setShuffleEnabled(shuffling);
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
