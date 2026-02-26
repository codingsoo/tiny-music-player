package com.martinmimigames.tinymusicplayer;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.widget.RemoteViews;

import java.io.File;

import mg.utils.notify.NotificationHelper;

class Notifications {

  public static final String NOTIFICATION_CHANNEL = "nc";
  public static final int NOTIFICATION_ID = 1;
  private static final String TAP_TO_CLOSE = "Tap to close";
  private final Service service;
  Notification notification;
  Notification.Builder builder;

  public Notifications(Service service) {
    this.service = service;
  }

  public void create() {
    if (Build.VERSION.SDK_INT >= 26) {
      var name = "Playback Control";
      var description = "Notification audio controls";
      var importance = NotificationManager.IMPORTANCE_LOW;
      var notificationChannel = NotificationHelper.setupNotificationChannel(service, NOTIFICATION_CHANNEL, name, description, importance);
      notificationChannel.setSound(null, null);
      notificationChannel.setVibrationPattern(null);
    }
  }

  void setupNotificationBuilder(String title, PendingIntent playPauseIntent, PendingIntent killIntent, PendingIntent loopIntent, PendingIntent shuffleIntent) {
    if (Build.VERSION.SDK_INT < 11) return;

    if (Build.VERSION.SDK_INT >= 26) {
      builder = new Notification.Builder(service, NOTIFICATION_CHANNEL);
    } else {
      builder = new Notification.Builder(service);
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      builder.setCategory(Notification.CATEGORY_SERVICE);
    }

    builder.setSmallIcon(R.drawable.ic_notif);
    builder.setContentTitle(title);
    builder.setSound(null);
    builder.setVibrate(null);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      builder.setContentIntent(playPauseIntent);
      builder.addAction(0, "loop", loopIntent);
      builder.addAction(0, "shuffle", shuffleIntent);
      builder.addAction(0, TAP_TO_CLOSE, killIntent);
    } else {
      builder.setContentText(TAP_TO_CLOSE);
      builder.setContentIntent(killIntent);
    }
  }

  void setState(boolean playing, boolean looping, boolean shuffling) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      var playbackText = "Tap to ";
      playbackText += (playing) ? "pause" : "play";
      if (looping) {
        playbackText += " | looping";
      }
      if (shuffling) {
        playbackText += " | shuffle";
      }
      builder.setContentText(playbackText);
      buildNotification();
      update();
    }
  }

  PendingIntent genIntent(int id, byte action) {
    var pendingIntentFlag = PendingIntent.FLAG_IMMUTABLE;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE)
      pendingIntentFlag |= PendingIntent.FLAG_UPDATE_CURRENT;

    var intentFlag = Intent.FLAG_ACTIVITY_NO_HISTORY;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR)
      intentFlag |= Intent.FLAG_ACTIVITY_NO_ANIMATION;

    return PendingIntent
      .getService(service, id, new Intent(service, Service.class)
          .addFlags(intentFlag)
          .putExtra(Launcher.TYPE, action)
        , pendingIntentFlag);
  }

  void genNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      buildNotification();
    } else {
      notification = new Notification();
    }
  }

  void buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
      notification = builder.build();
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
      notification = builder.getNotification();
    }
  }

  void setupNotification(String title, PendingIntent killIntent) {
    if (Build.VERSION.SDK_INT < 11) {
      notification.contentView = new RemoteViews("com.martinmimigames.tinymusicplayer", R.layout.notif);
      notification.icon = R.drawable.ic_notif;
      notification.audioStreamType = AudioManager.STREAM_MUSIC;
      notification.sound = null;
      notification.contentIntent = killIntent;
      notification.contentView.setTextViewText(R.id.notif_title, title);
      notification.vibrate = null;
    }
  }

  void getNotification(final Uri uri) {
    var title = new File(uri.getPath()).getName();

    var killIntent = genIntent(1, Launcher.KILL);
    var playPauseIntent = genIntent(2, Launcher.PLAY_PAUSE);
    var loopIntent = genIntent(3, Launcher.LOOP);
    var shuffleIntent = genIntent(4, Launcher.SHUFFLE);

    setupNotificationBuilder(title, playPauseIntent, killIntent, loopIntent, shuffleIntent);
    genNotification();
    setupNotification(title, killIntent);

    update();
  }

  private void update() {
    NotificationHelper.send(service, NOTIFICATION_ID, notification);
  }

  void destroy() {
    NotificationHelper.unsend(service, NOTIFICATION_ID);
  }
}
