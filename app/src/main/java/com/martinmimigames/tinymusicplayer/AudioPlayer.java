package com.martinmimigames.tinymusicplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.io.IOException;

class AudioPlayer extends Thread implements MediaPlayer.OnCompletionListener {

  private final Service service;
  private MediaPlayer mediaPlayer;
  private Uri currentUri;
  private boolean isLooping;
  private boolean isPrepared;

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
    this.currentUri = audioLocation;
    this.isLooping = false;
    this.isPrepared = false;
    
    initializeMediaPlayer(audioLocation);
  }

  /**
   * Initialize or reinitialize the media player with a new audio source
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
      isPrepared = true;
      service.setState(true, isLooping, service.isShuffleEnabled());
    } catch (IllegalStateException e) {
      Exceptions.throwError(service, Exceptions.IllegalState);
    } catch (IOException e) {
      Exceptions.throwError(service, Exceptions.IO);
    }
  }

  /**
   * Play a new track
   *
   * @param audioLocation the URI of the new track
   * @return true if successful, false otherwise
   */
  public boolean playNewTrack(Uri audioLocation) {
    if (audioLocation == null) return false;
    
    try {
      // Release old media player
      if (mediaPlayer != null) {
        if (isPrepared) {
          mediaPlayer.stop();
        }
        mediaPlayer.release();
      }
      
      isPrepared = false;
      currentUri = audioLocation;
      
      // Initialize with new track
      initializeMediaPlayer(audioLocation);
      
      // Prepare and start
      mediaPlayer.prepare();
      isPrepared = true;
      mediaPlayer.start();
      
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /**
   * Get the current track URI
   */
  public Uri getCurrentUri() {
    return currentUri;
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
    return isLooping;
  }

  /**
   * set player state
   *
   * @param playing is audio playing
   * @param looping is audio looping
   */
  void setState(boolean playing, boolean looping) {
    this.isLooping = looping;
    if (playing) {
      mediaPlayer.start();
    } else {
      mediaPlayer.pause();
    }
    // Note: We don't use MediaPlayer's built-in looping anymore
    // because we handle it ourselves to support playlist looping
    mediaPlayer.setLooping(false);
  }

  /**
   * release resource when playback finished
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    // Notify service that track completed - service will decide what to do
    service.onTrackCompleted();
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
