package com.martinmimigames.tinymusicplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

class AudioPlayer extends Thread implements MediaPlayer.OnCompletionListener {

  private static final String[] AUDIO_EXTENSIONS = {
    "mp3", "wav", "ogg", "flac", "aac", "m4a", "wma", "opus", "mid", "midi", "amr", "3gp"
  };

  private final Service service;
  private final MediaPlayer mediaPlayer;
  private Uri currentUri;
  private boolean shuffling;

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
      service.setState(true, false, service.getShuffling());
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
  public boolean isShuffling() {
    return shuffling;
  }

  /**
   * get the current audio URI
   */
  public Uri getCurrentUri() {
    return currentUri;
  }

  /**
   * set player state
   *
   * @param playing   is audio playing
   * @param looping   is audio looping
   * @param shuffling is shuffle mode enabled
   */
  void setState(boolean playing, boolean looping, boolean shuffling) {
    if (playing) {
      mediaPlayer.start();
    } else {
      mediaPlayer.pause();
    }
    mediaPlayer.setLooping(looping);
    this.shuffling = shuffling;
  }

  /**
   * Get sibling audio files in the same directory as the current track
   */
  private List<File> getSiblingAudioFiles() {
    var path = currentUri.getPath();
    if (path == null) return new ArrayList<>();

    var currentFile = new File(path);
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) return new ArrayList<>();

    var files = parentDir.listFiles();
    if (files == null) return new ArrayList<>();

    var siblings = new ArrayList<File>();
    for (var file : files) {
      if (!file.isFile()) continue;
      if (file.getAbsolutePath().equals(currentFile.getAbsolutePath())) continue;

      var name = file.getName().toLowerCase(Locale.ROOT);
      var dotIndex = name.lastIndexOf('.');
      if (dotIndex < 0) continue;
      var ext = name.substring(dotIndex + 1);

      for (var audioExt : AUDIO_EXTENSIONS) {
        if (audioExt.equals(ext)) {
          siblings.add(file);
          break;
        }
      }
    }
    return siblings;
  }

  /**
   * Play a random sibling audio file from the same directory
   */
  void playRandomSibling() {
    try {
      var siblings = getSiblingAudioFiles();
      if (siblings.isEmpty()) {
        service.stopSelf();
        return;
      }

      var randomFile = siblings.get(new Random().nextInt(siblings.size()));
      var newUri = Uri.fromFile(randomFile);

      mediaPlayer.reset();
      mediaPlayer.setDataSource(service, newUri);

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

      mediaPlayer.setLooping(isLooping());
      mediaPlayer.setOnCompletionListener(this);
      mediaPlayer.prepare();
      mediaPlayer.start();

      currentUri = newUri;
      service.onTrackChanged(newUri);
    } catch (Exception e) {
      service.stopSelf();
    }
  }

  /**
   * release resource when playback finished
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    if (shuffling) {
      playRandomSibling();
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
