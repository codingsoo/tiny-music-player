package com.martinmimigames.tinymusicplayer;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

public class Service extends android.app.Service {

  final HWListener hwListener;
  final Notifications notifications;
  private final Random random;
  private AudioPlayer audioPlayer;
  private boolean shuffleEnabled;

  public Service() {
    hwListener = new HWListener(this);
    notifications = new Notifications(this);
    random = new Random();
    shuffleEnabled = false;
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onCreate() {
    hwListener.create();
    notifications.create();

    super.onCreate();
  }

  @Override
  public void onStart(final Intent intent, final int startId) {
    if (intent.getAction() == null) {
      var isPlaying = audioPlayer.isPlaying();
      var isLooping = audioPlayer.isLooping();
      switch (intent.getByteExtra(Launcher.TYPE, Launcher.NULL)) {
        case Launcher.PLAY_PAUSE -> setState(!isPlaying, isLooping);
        case Launcher.PLAY -> setState(true, isLooping);
        case Launcher.PAUSE -> setState(false, isLooping);
        case Launcher.LOOP -> setState(isPlaying, !isLooping);
        case Launcher.SHUFFLE -> toggleShuffle();
        case Launcher.KILL -> stopSelf();
      }
    } else {
      switch (intent.getAction()) {
        case Intent.ACTION_VIEW -> setAudio(intent.getData());
        case Intent.ACTION_SEND -> setAudio(intent.getParcelableExtra(Intent.EXTRA_STREAM));
      }
    }
  }

  void toggleShuffle() {
    shuffleEnabled = !shuffleEnabled;
    var isPlaying = audioPlayer.isPlaying();
    var isLooping = audioPlayer.isLooping();
    audioPlayer.setState(isPlaying, isLooping);
    hwListener.setState(isPlaying, isLooping);
    notifications.setState(isPlaying, isLooping, shuffleEnabled);
  }

  boolean isShuffleEnabled() {
    return shuffleEnabled;
  }

  void setAudio(final Uri audioLocation) {
    try {
      audioPlayer = new AudioPlayer(this, audioLocation);
      audioPlayer.start();

      notifications.getNotification(audioLocation);

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

  void setState(boolean playing, boolean looping) {
    audioPlayer.setState(playing, looping);
    hwListener.setState(playing, looping);
    notifications.setState(playing, looping, shuffleEnabled);
  }

  void playNextShuffleTrack() {
    Uri nextTrack = getRandomAudioUri();
    if (nextTrack != null) {
      if (!audioPlayer.isInterrupted()) {
        audioPlayer.interrupt();
      }
      setAudio(nextTrack);
    } else {
      stopSelf();
    }
  }

  private Uri getRandomAudioUri() {
    ArrayList<Uri> audioUris = new ArrayList<>();
    ContentResolver contentResolver = getContentResolver();
    Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    String[] projection = {MediaStore.Audio.Media._ID};
    String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";

    try {
      Cursor cursor = contentResolver.query(collection, projection, selection, null, null);
      if (cursor != null) {
        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
        while (cursor.moveToNext()) {
          long id = cursor.getLong(idColumn);
          Uri contentUri = Uri.withAppendedPath(collection, String.valueOf(id));
          audioUris.add(contentUri);
        }
        cursor.close();
      }
    } catch (Exception e) {
      return null;
    }

    if (audioUris.isEmpty()) {
      return null;
    }

    int randomIndex = random.nextInt(audioUris.size());
    return audioUris.get(randomIndex);
  }

  @TargetApi(Build.VERSION_CODES.ECLAIR)
  @Override
  public int onStartCommand(final Intent intent, final int flags, final int startId) {
    onStart(intent, startId);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    notifications.destroy();
    hwListener.destroy();
    if (!audioPlayer.isInterrupted()) audioPlayer.interrupt();

    super.onDestroy();
  }
}
