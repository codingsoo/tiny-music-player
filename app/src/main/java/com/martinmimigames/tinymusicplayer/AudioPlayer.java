package com.martinmimigames.tinymusicplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

class AudioPlayer extends Thread implements MediaPlayer.OnCompletionListener {

  private final Service service;
  private MediaPlayer mediaPlayer;
  private final ArrayList<Uri> playlist;
  private int currentIndex;
  private boolean shuffleEnabled;
  private final Random random;

  /**
   * Initiate an audio player, throws exceptions if failed.
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
    this.random = new Random();
    
    initMediaPlayer(audioLocation);
  }

  /**
   * Initiate an audio player with multiple audio files.
   *
   * @param service        the service initialising this.
   * @param audioLocations the list of Uris containing the locations of the audio files.
   * @throws IllegalArgumentException when the media player need cookies, but we do not supply it.
   * @throws IllegalStateException    when the media player is not in the correct state.
   * @throws SecurityException        when the audio file is protected and cannot be played.
   * @throws IOException              when the audio file cannot be read.
   */
  public AudioPlayer(Service service, ArrayList<Uri> audioLocations) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
    this.service = service;
    this.playlist = new ArrayList<>(audioLocations);
    this.currentIndex = 0;
    this.shuffleEnabled = false;
    this.random = new Random();
    
    if (playlist.isEmpty()) {
      throw new IllegalArgumentException("Playlist cannot be empty");
    }
    
    initMediaPlayer(playlist.get(currentIndex));
  }

  /**
   * Initialize the media player with the given audio location.
   */
  private void initMediaPlayer(Uri audioLocation) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
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
      service.setState(true, false, shuffleEnabled);
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
    return mediaPlayer.isPlaying();
  }

  /**
   * check if audio is looping, always false on < android cupcake (sdk 3)
   */
  public boolean isLooping() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
      return mediaPlayer.isLooping();
    } else {
      return false;
    }
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
    if (playing) {
      mediaPlayer.start();
    } else {
      mediaPlayer.pause();
    }
    mediaPlayer.setLooping(looping);
  }

  /**
   * Toggle shuffle mode
   */
  void setShuffleEnabled(boolean enabled) {
    this.shuffleEnabled = enabled;
  }

  /**
   * Get the current audio URI
   */
  Uri getCurrentUri() {
    return playlist.get(currentIndex);
  }

  /**
   * Get the playlist size
   */
  int getPlaylistSize() {
    return playlist.size();
  }

  /**
   * Play the next track in the playlist.
   * If shuffle is enabled, picks a random track.
   * Returns true if successfully moved to next track, false if at end of playlist.
   */
  boolean playNext() {
    if (playlist.size() <= 1) {
      return false;
    }

    int nextIndex;
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      do {
        nextIndex = random.nextInt(playlist.size());
      } while (nextIndex == currentIndex && playlist.size() > 1);
    } else {
      nextIndex = (currentIndex + 1) % playlist.size();
    }

    return switchToTrack(nextIndex);
  }

  /**
   * Play the previous track in the playlist.
   * If shuffle is enabled, picks a random track.
   * Returns true if successfully moved to previous track, false otherwise.
   */
  boolean playPrevious() {
    if (playlist.size() <= 1) {
      return false;
    }

    int prevIndex;
    if (shuffleEnabled) {
      // True random: pick any track except the current one
      do {
        prevIndex = random.nextInt(playlist.size());
      } while (prevIndex == currentIndex && playlist.size() > 1);
    } else {
      prevIndex = (currentIndex - 1 + playlist.size()) % playlist.size();
    }

    return switchToTrack(prevIndex);
  }

  /**
   * Switch to a specific track in the playlist.
   */
  private boolean switchToTrack(int index) {
    if (index < 0 || index >= playlist.size()) {
      return false;
    }

    boolean wasPlaying = mediaPlayer.isPlaying();
    boolean wasLooping = isLooping();

    // Release current media player
    mediaPlayer.release();

    currentIndex = index;

    try {
      initMediaPlayer(playlist.get(currentIndex));
      mediaPlayer.prepare();
      if (wasPlaying) {
        mediaPlayer.start();
      }
      mediaPlayer.setLooping(wasLooping);
      service.onTrackChanged(playlist.get(currentIndex));
      return true;
    } catch (IOException e) {
      Exceptions.throwError(service, Exceptions.IO);
      return false;
    } catch (IllegalStateException e) {
      Exceptions.throwError(service, Exceptions.IllegalState);
      return false;
    }
  }

  /**
   * release resource when playback finished
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    // If looping is enabled, MediaPlayer handles it automatically
    // If shuffle is enabled and we have multiple tracks, play next random track
    if (shuffleEnabled && playlist.size() > 1) {
      playNext();
    } else if (playlist.size() > 1 && !isLooping()) {
      // Move to next track in sequential order
      int nextIndex = currentIndex + 1;
      if (nextIndex < playlist.size()) {
        switchToTrack(nextIndex);
      } else {
        // End of playlist
        service.stopSelf();
      }
    } else {
      service.stopSelf();
    }
  }

  /**
   * release and kill service
   */
  @Override
  public void interrupt() {
    mediaPlayer.release();
    super.interrupt();
  }
}
