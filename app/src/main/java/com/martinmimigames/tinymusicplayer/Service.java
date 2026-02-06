package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;
import java.util.ArrayList;

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
    playlistManager = new PlaylistManager();
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
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping, isShuffling);
        case Launcher.PLAY -> setState(true, isLooping, isShuffling);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffling);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, isShuffling);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !isShuffling);
        case Launcher.NEXT -> playNext();
        case Launcher.PREVIOUS -> playPrevious();
        /* cancel audio playback and kill service */
        case Launcher.KILL -> stopSelf();
      }
    } else {
      switch (intent.getAction()) {
        case Intent.ACTION_VIEW -> {
          // Check for playlist first
          ArrayList<Uri> playlist = intent.getParcelableArrayListExtra("playlist");
          if (playlist != null && !playlist.isEmpty()) {
            setPlaylist(playlist);
          } else if (intent.getData() != null) {
            // Single file
            ArrayList<Uri> singleFile = new ArrayList<>();
            singleFile.add(intent.getData());
            setPlaylist(singleFile);
          }
        }
        case Intent.ACTION_SEND -> {
          Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
          if (uri != null) {
            ArrayList<Uri> singleFile = new ArrayList<>();
            singleFile.add(uri);
            setPlaylist(singleFile);
          }
        }
      }
    }
  }

  /**
   * Set the playlist and start playing the first track
   */
  void setPlaylist(ArrayList<Uri> uris) {
    playlistManager.setPlaylist(uris);
    Uri firstTrack = playlistManager.getCurrentTrack();
    if (firstTrack != null) {
      setAudio(firstTrack);
    }
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
      notifications.getNotification(audioLocation, playlistManager.size() > 1);

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
   * Called when the current track completes playback
   */
  void onTrackCompleted() {
    if (playlistManager.size() > 1) {
      // Play next track (random if shuffle enabled)
      playNext();
    } else {
      // Single track, stop service
      stopSelf();
    }
  }

  /**
   * Play the next track in the playlist
   */
  void playNext() {
    Uri nextTrack = playlistManager.getNextTrack();
    if (nextTrack != null) {
      setAudio(nextTrack);
    } else {
      stopSelf();
    }
  }

  /**
   * Play the previous track in the playlist
   */
  void playPrevious() {
    Uri prevTrack = playlistManager.getPreviousTrack();
    if (prevTrack != null) {
      setAudio(prevTrack);
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    if (audioPlayer != null) {
      audioPlayer.setState(playing, looping);
    }
    playlistManager.setShuffleEnabled(shuffling);
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
    playlistManager.clear();

    super.onDestroy();
  }
}
