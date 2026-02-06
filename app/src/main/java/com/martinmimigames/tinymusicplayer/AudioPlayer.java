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
  private Uri currentAudioLocation;

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
    this.currentAudioLocation = audioLocation;
    /* initiate new audio player */
    mediaPlayer = new MediaPlayer();

    setupMediaPlayer(audioLocation);
  }

  /**
   * Setup the media player with the given audio location.
   */
  private void setupMediaPlayer(Uri audioLocation) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
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

  /**
   * Change to a new audio track.
   *
   * @param audioLocation the Uri of the new audio track
   * @throws IllegalArgumentException when the media player need cookies, but we do not supply it.
   * @throws IllegalStateException    when the media player is not in the correct state.
   * @throws SecurityException        when the audio file is protected and cannot be played.
   * @throws IOException              when the audio file cannot be read.
   */
  public void changeTrack(Uri audioLocation) throws IllegalArgumentException, IllegalStateException, SecurityException, IOException {
    this.currentAudioLocation = audioLocation;
    mediaPlayer.reset();
    setupMediaPlayer(audioLocation);
    mediaPlayer.prepare();
    mediaPlayer.start();
  }

  /**
   * Get the current audio location.
   *
   * @return the current audio URI
   */
  public Uri getCurrentAudioLocation() {
    return currentAudioLocation;
  }

  @Override
  public void run() {
    /* get ready for playback */
    try {
      mediaPlayer.prepare();
      service.setState(true, false, service.playlist.isShuffleEnabled());
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
   * Called when playback finishes - notify service to handle next track or stop
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    service.onTrackCompleted();
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
