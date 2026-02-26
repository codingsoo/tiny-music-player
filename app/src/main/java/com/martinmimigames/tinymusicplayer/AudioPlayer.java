package com.martinmimigames.tinymusicplayer;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

class AudioPlayer extends Thread implements MediaPlayer.OnCompletionListener {

  private static final String[] AUDIO_EXTENSIONS = {
    ".mp3", ".wav", ".ogg", ".flac", ".aac", ".m4a", ".wma", ".opus", ".mid", ".midi"
  };

  private final Service service;
  private final MediaPlayer mediaPlayer;
  private final Uri audioLocation;
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
    this.audioLocation = audioLocation;
    this.shuffling = false;
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
      service.setState(true, false, service.shuffling);
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
   * release resource when playback finished
   */
  @Override
  public void onCompletion(MediaPlayer mp) {
    if (shuffling && "file".equals(audioLocation.getScheme())) {
      var nextFile = pickRandomAudioFile();
      if (nextFile != null) {
        service.setAudio(Uri.fromFile(nextFile));
        return;
      }
    }
    service.stopSelf();
  }

  /**
   * Pick a random audio file from the same directory as the current file.
   * Excludes the current file unless it is the only audio file.
   *
   * @return a random audio File, or null if none found
   */
  private File pickRandomAudioFile() {
    var currentFile = new File(audioLocation.getPath());
    var parentDir = currentFile.getParentFile();
    if (parentDir == null || !parentDir.isDirectory()) {
      return null;
    }

    var files = parentDir.listFiles();
    if (files == null) {
      return null;
    }

    var audioFiles = new ArrayList<File>();
    for (var file : files) {
      if (file.isFile() && isAudioFile(file.getName())) {
        audioFiles.add(file);
      }
    }

    if (audioFiles.isEmpty()) {
      return null;
    }

    /* if only one audio file exists, replay it */
    if (audioFiles.size() == 1) {
      return audioFiles.get(0);
    }

    /* exclude current file to avoid playing the same track twice */
    audioFiles.remove(currentFile);

    if (audioFiles.isEmpty()) {
      return currentFile;
    }

    var random = new Random();
    return audioFiles.get(random.nextInt(audioFiles.size()));
  }

  /**
   * Check if a filename has a common audio extension
   */
  private static boolean isAudioFile(String name) {
    var lowerName = name.toLowerCase();
    for (var ext : AUDIO_EXTENSIONS) {
      if (lowerName.endsWith(ext)) {
        return true;
      }
    }
    return false;
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
