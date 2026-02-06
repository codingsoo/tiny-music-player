package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
   * shuffle mode state
   */
  private boolean isShuffleEnabled = false;
  
  /**
   * playlist of audio files from the same folder
   */
  private List<Uri> playlist = new ArrayList<>();
  
  /**
   * current track index in playlist
   */
  private int currentTrackIndex = -1;
  
  /**
   * random number generator for shuffle mode
   */
  private final Random random = new Random();

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
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
      var isPlaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        /* start or pause audio playback */
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping, isShuffleEnabled);
        case Launcher.PLAY -> setState(true, isLooping, isShuffleEnabled);
        case Launcher.PAUSE -> setState(false, isLooping, isShuffleEnabled);
        case Launcher.LOOP -> setState(isPlaying, !isLooping, isShuffleEnabled);
        case Launcher.SHUFFLE -> setState(isPlaying, isLooping, !isShuffleEnabled);
        case Launcher.SKIP_NEXT -> skipToNext();
        case Launcher.SKIP_PREV -> skipToPrevious();
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
      // Build playlist from the same folder
      buildPlaylist(audioLocation);
      
      /* get audio playback logic and start async */
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      /* create notification for playback control */
      notifications.getNotification(audioLocation);

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
   * Build a playlist from audio files in the same folder as the selected file
   */
  private void buildPlaylist(Uri audioLocation) {
    playlist.clear();
    currentTrackIndex = -1;
    
    // Add the current track
    playlist.add(audioLocation);
    currentTrackIndex = 0;
    
    try {
      String scheme = audioLocation.getScheme();
      
      if (ContentResolver.SCHEME_FILE.equals(scheme)) {
        // File URI - scan the directory
        buildPlaylistFromFile(audioLocation);
      } else if (ContentResolver.SCHEME_CONTENT.equals(scheme)) {
        // Content URI - try to get files from the same folder via MediaStore
        buildPlaylistFromContent(audioLocation);
      }
    } catch (Exception e) {
      // If we fail to build playlist, just keep the single track
    }
  }
  
  /**
   * Build playlist from file:// URI by scanning the parent directory
   */
  private void buildPlaylistFromFile(Uri audioLocation) {
    File currentFile = new File(audioLocation.getPath());
    File parentDir = currentFile.getParentFile();
    
    if (parentDir != null && parentDir.isDirectory()) {
      File[] files = parentDir.listFiles((dir, name) -> {
        String lowerName = name.toLowerCase();
        return lowerName.endsWith(".mp3") || lowerName.endsWith(".wav") ||
               lowerName.endsWith(".ogg") || lowerName.endsWith(".flac") ||
               lowerName.endsWith(".m4a") || lowerName.endsWith(".aac") ||
               lowerName.endsWith(".wma") || lowerName.endsWith(".opus");
      });
      
      if (files != null) {
        playlist.clear();
        for (int i = 0; i < files.length; i++) {
          Uri fileUri = Uri.fromFile(files[i]);
          playlist.add(fileUri);
          if (files[i].equals(currentFile)) {
            currentTrackIndex = i;
          }
        }
      }
    }
  }
  
  /**
   * Build playlist from content:// URI using MediaStore
   */
  private void buildPlaylistFromContent(Uri audioLocation) {
    // Try to get the file path from the content URI
    String[] projection = {MediaStore.Audio.Media.DATA};
    
    try (Cursor cursor = getContentResolver().query(audioLocation, projection, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
        String filePath = cursor.getString(columnIndex);
        
        if (filePath != null) {
          File currentFile = new File(filePath);
          File parentDir = currentFile.getParentFile();
          
          if (parentDir != null) {
            // Query MediaStore for all audio files in the same directory
            String selection = MediaStore.Audio.Media.DATA + " LIKE ?";
            String[] selectionArgs = {parentDir.getAbsolutePath() + "/%"};
            String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";
            
            try (Cursor audioCursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA},
                selection,
                selectionArgs,
                sortOrder)) {
              
              if (audioCursor != null) {
                playlist.clear();
                int idColumn = audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int dataColumn = audioCursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
                
                int index = 0;
                while (audioCursor.moveToNext()) {
                  long id = audioCursor.getLong(idColumn);
                  String path = audioCursor.getString(dataColumn);
                  Uri contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                  playlist.add(contentUri);
                  
                  if (path != null && path.equals(filePath)) {
                    currentTrackIndex = index;
                  }
                  index++;
                }
              }
            }
          }
        }
      }
    } catch (Exception e) {
      // Keep the single track if we fail
    }
  }

  /**
   * Switch to player component state
   */
  void setState(boolean playing, boolean looping, boolean shuffle) {
    isShuffleEnabled = shuffle;
    audioPlayer.setState(playing, looping);
    hwListener.setState(playing, looping, shuffle);
    notifications.setState(playing, looping, shuffle);
  }
  
  /**
   * Called when the current track completes playback
   */
  void onTrackCompleted() {
    if (isShuffleEnabled && playlist.size() > 1) {
      // Shuffle mode: play a random track
      playRandomTrack();
    } else if (playlist.size() > 1 && currentTrackIndex < playlist.size() - 1) {
      // Sequential mode: play next track if available
      playTrackAtIndex(currentTrackIndex + 1);
    } else {
      // No more tracks or single track - stop
      stopSelf();
    }
  }
  
  /**
   * Skip to the next track
   */
  private void skipToNext() {
    if (playlist.isEmpty()) {
      return;
    }
    
    if (isShuffleEnabled) {
      playRandomTrack();
    } else {
      int nextIndex = (currentTrackIndex + 1) % playlist.size();
      playTrackAtIndex(nextIndex);
    }
  }
  
  /**
   * Skip to the previous track
   */
  private void skipToPrevious() {
    if (playlist.isEmpty()) {
      return;
    }
    
    if (isShuffleEnabled) {
      playRandomTrack();
    } else {
      int prevIndex = (currentTrackIndex - 1 + playlist.size()) % playlist.size();
      playTrackAtIndex(prevIndex);
    }
  }
  
  /**
   * Play a random track from the playlist (true random)
   */
  private void playRandomTrack() {
    if (playlist.size() <= 1) {
      return;
    }
    
    // Generate a random index different from current
    int randomIndex;
    do {
      randomIndex = random.nextInt(playlist.size());
    } while (randomIndex == currentTrackIndex && playlist.size() > 1);
    
    playTrackAtIndex(randomIndex);
  }
  
  /**
   * Play the track at the specified index
   */
  private void playTrackAtIndex(int index) {
    if (index < 0 || index >= playlist.size()) {
      return;
    }
    
    // Stop current playback
    if (audioPlayer != null && !audioPlayer.isInterrupted()) {
      audioPlayer.interrupt();
    }
    
    currentTrackIndex = index;
    Uri nextTrack = playlist.get(index);
    
    try {
      audioPlayer = new AudioPlayer(this, nextTrack);
      audioPlayer.start();
      
      // Update notification with new track
      notifications.getNotification(nextTrack);
      notifications.setState(true, audioPlayer.isLooping(), isShuffleEnabled);
      
    } catch (Exception e) {
      Exceptions.throwError(this, Exceptions.IO);
    }
  }
  
  /**
   * Check if shuffle mode is enabled
   */
  boolean isShuffleEnabled() {
    return isShuffleEnabled;
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
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

    super.onDestroy();
  }
}
