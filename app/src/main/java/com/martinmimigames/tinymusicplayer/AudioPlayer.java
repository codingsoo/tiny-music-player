package com.martinmimigames.tinymusicplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

class AudioPlayer extends Thread implements MediaPlayer.OnCompletionListener {

  private final Service service;
  private MediaPlayer mediaPlayer;
  private final List<Uri> playlist;
  private int currentIndex;
  private boolean shuffleEnabled;
  private boolean looping;
  private final Random random;

  /**
   * Initiate an audio player with a single track.
   *
   * @param service       the service initialising this.
   * @param audioLocation the Uri containing the location of the audio.
   * @throws IllegalArgumentException when the media player need cookies, but we do not supply it.
   * @throws IllegalStateException    when the media player is not in the correct state.
   * @throws SecurityException        when the audio file is protected and cannot be played.
   * @throws IOException              when the audio file cannot be read.
   */
  public AudioPlayer(Service service, Uri audioLocation) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
    this.service = service;
    this.playlist = new ArrayList<>();
    this.playlist.add(audioLocation);
    this.currentIndex = 0;
    this.shuffleEnabled = false;
    this.looping = false;
    this.random = new Random();
    
    initializeMediaPlayer(audioLocation);
  }

  /**
   * Initiate an audio player with multiple tracks (playlist).
   *
   * @param service the service initialising this.
   * @param tracks  list of Uri containing the locations of the audio files.
   * @throws IllegalArgumentException when the media player need cookies, but we do not supply it.
   * @throws IllegalStateException    when the media player is not in the correct state.
   * @throws SecurityException        when the audio file is protected and cannot be played.
   * @throws IOException              when the audio file cannot be read.
   */
  public AudioPlayer(Service service, List<Uri> tracks) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
    this.service = service;
    this.playlist = new ArrayList<>(tracks);
    this.currentIndex = 0;
    this.shuffleEnabled = false;
    this.looping = false;
    this.random = new Random();
    
    if (!playlist.isEmpty()) {
      initializeMediaPlayer(playlist.get(0));
    }
  }

  /**
   * Initialize the media player with the given audio location.
   */
  private void initializeMediaPlayer(Uri audioLocation) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
    /* initiate new audio player */
    mediaPlayer = new MediaPlayer();

    /* setup player variables */
    mediaPlayer.setDataSource(service, audioLocation);

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
      mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
    } else {
      mediaPlayer.setAudioAttributes(
        new AudioAttributes.Builder()
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .build()
      );
    }

    mediaPlayer.setLooping(false);

    /* setup listeners for further logics */
    mediaPlayer.setOnCompletionListener(this);
  }

  @Override
  public void run() {
    /* get ready for playback */
    try {
      mediaPlayer.prepare();
      service.setState(true, looping, shuffleEnabled);
    } catch (IllegalStateException e) {
      Exceptions.throwError(service, Exceptions.IllegalState);
    } catch (IOException e) {
      Exceptions.throwError(service, Exceptions.IO);
    }
  }

  /**
   * check if audio is playing
   */
  public boolean isPlaying() {
    return mediaPlayer != null && mediaPlayer.isPlaying();
  }

  /**
   * check if audio is looping, always false on < android cupcake (sdk 3)
   */
  public boolean isLooping() {
    return looping;
  }

  /**
   * check if shuffle mode is enabled
   */
  public boolean isShuffleEnabled() {
    return shuffleEnabled;
  }

  /**
   * set player state
   *
   * @param playing is audio playing
   * @param looping is audio looping
   */
  void setState(boolean playing, boolean looping) {
    this.looping = looping;
    if (playing) {
      mediaPlayer.start();
    } else {
      mediaPlayer.pause();
    }
    // Note: We don't use MediaPlayer's setLooping when we have a playlist
    // because we handle looping logic ourselves in onCompletion
    if (playlist.size() == 1) {
      mediaPlayer.setLooping(looping);
    }
  }

  /**
   * Set shuffle mode
   *
   * @param enabled true to enable shuffle, false to disable
   */
  void setShuffleEnabled(boolean enabled) {
    this.shuffleEnabled = enabled;
  }

  /**
   * Get the current track URI
   */
  public Uri getCurrentTrack() {
    if (currentIndex >= 0 && currentIndex < playlist.size()) {
      return playlist.get(currentIndex);
    }
    return null;
  }

  /**
   * Get the number of tracks in the playlist
   */
  public int getPlaylistSize() {
    return playlist.size();
  }

  /**
   * Skip to the next track.
   * If shuffle is enabled, picks a random track.
   * If at the end of playlist and not looping, stops playback.
   *
   * @return true if successfully skipped to next track, false if no more tracks
   */
  public boolean skipNext() {
    if (playlist.size() <= 1) {
      // Single track - if looping, restart; otherwise stop
      if (looping) {
        try {
          mediaPlayer.seekTo(0);
          mediaPlayer.start();
          return true;
        } catch (Exception e) {
          return false;
        }
      }
      return false;
    }

    int nextIndex;
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      if (playlist.size() == 2) {
        nextIndex = (currentIndex + 1) % 2;
      } else {
        do {
          nextIndex = random.nextInt(playlist.size());
        } while (nextIndex == currentIndex);
      }
    } else {
      // Sequential: go to next track
      nextIndex = currentIndex + 1;
      if (nextIndex >= playlist.size()) {
        if (looping) {
          nextIndex = 0;
        } else {
          return false;
        }
      }
    }

    return playTrackAtIndex(nextIndex);
  }

  /**
   * Skip to the previous track.
   * If shuffle is enabled, picks a random track.
   *
   * @return true if successfully skipped to previous track
   */
  public boolean skipPrevious() {
    if (playlist.size() <= 1) {
      // Single track - restart from beginning
      try {
        mediaPlayer.seekTo(0);
        mediaPlayer.start();
        return true;
      } catch (Exception e) {
        return false;
      }
    }

    int prevIndex;
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      if (playlist.size() == 2) {
        prevIndex = (currentIndex + 1) % 2;
      } else {
        do {
          prevIndex = random.nextInt(playlist.size());
        } while (prevIndex == currentIndex);
      }
    } else {
      // Sequential: go to previous track
      prevIndex = currentIndex - 1;
      if (prevIndex < 0) {
        if (looping) {
          prevIndex = playlist.size() - 1;
        } else {
          // Restart current track from beginning
          try {
            mediaPlayer.seekTo(0);
            mediaPlayer.start();
            return true;
          } catch (Exception e) {
            return false;
          }
        }
      }
    }

    return playTrackAtIndex(prevIndex);
  }

  /**
   * Play the track at the specified index
   *
   * @param index the index of the track to play
   * @return true if successful
   */
  private boolean playTrackAtIndex(int index) {
    if (index < 0 || index >= playlist.size()) {
      return false;
    }

    try {
      // Release current media player
      if (mediaPlayer != null) {
        mediaPlayer.release();
      }

      currentIndex = index;
      Uri trackUri = playlist.get(index);
      
      // Initialize new media player for the track
      initializeMediaPlayer(trackUri);
      mediaPlayer.prepare();
      mediaPlayer.start();
      
      // Update notification with new track info
      service.onTrackChanged(trackUri);
      
      return true;
    } catch (Exception e) {
      Exceptions.throwError(service, Exceptions.IO);
      return false;
    }
  }

  /**
   * Add tracks to the playlist
   *
   * @param tracks list of track URIs to add
   */
  public void addToPlaylist(List<Uri> tracks) {
    playlist.addAll(tracks);
  }

  /**
   * release resource when playback finished
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    if (playlist.size() > 1 || (shuffleEnabled && playlist.size() == 1)) {
      // Multiple tracks or shuffle mode - try to play next
      if (!skipNext()) {
        // No more tracks to play
        service.stopSelf();
      }
    } else if (looping) {
      // Single track with loop - MediaPlayer handles this
      // This shouldn't be called if looping is set on MediaPlayer
    } else {
      // Single track, no loop - stop
      service.stopSelf();
    }
  }

  /**
   * release and kill service
   */
  @Override
  public void interrupt() {
    if (mediaPlayer != null) {
      mediaPlayer.release();
    }
    super.interrupt();
  }
}
