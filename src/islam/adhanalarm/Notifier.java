package islam.adhanalarm;

import islam.adhanalarm.receiver.ClearNotificationReceiver;
import islam.adhanalarm.receiver.ClickNotificationReceiver;
import uz.efir.muazzin.Muazzin;
import uz.efir.muazzin.R;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;

public class Notifier {

    private static MediaPlayer mediaPlayer;
    private static Context context;
    private static Notification notification;

    public static void start(Context context, short timeIndex, long actualTime) {
        Notifier.context = context;

        if (timeIndex == CONSTANT.NEXT_FAJR) {
            timeIndex = CONSTANT.FAJR;
        }
        Preferences preferences = Preferences.getInstance(context);
        int notificationMethod = preferences.getNotificationMethod(timeIndex);
        if (notificationMethod == CONSTANT.NOTIFICATION_NONE) {
            WakeLock.release();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            notification = new Notification(R.drawable.ic_launcher, "", actualTime);
        } else {
            buildApi11PlusNotification(timeIndex, actualTime);
            //return;
        }
        notification.tickerText = context.getString(R.string.time_for, context.getString(CONSTANT.TIME_NAMES[timeIndex]));

        stopNotification(); // We put this after since we don't want to clear previous notifications unless we have to

        int ringerMode = ((AudioManager)context.getSystemService(Context.AUDIO_SERVICE)).getRingerMode();
        int callState = ((TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE)).getCallState();
        if ((notificationMethod == CONSTANT.NOTIFICATION_PLAY || notificationMethod == CONSTANT.NOTIFICATION_CUSTOM)
                && ringerMode != AudioManager.RINGER_MODE_SILENT
                && ringerMode != AudioManager.RINGER_MODE_VIBRATE
                && callState == TelephonyManager.CALL_STATE_IDLE) {
            notification.tickerText = notification.tickerText + " (" + context.getString(R.string.stop) + ")";
            int alarm = R.raw.beep;
            if (timeIndex <= CONSTANT.ISHAA && timeIndex >= CONSTANT.DHUHR) {
                alarm = R.raw.adhan;
            } else if(timeIndex == CONSTANT.FAJR) {
                alarm = R.raw.adhan_fajr;
            }
            if (notificationMethod == CONSTANT.NOTIFICATION_CUSTOM) {
                mediaPlayer = MediaPlayer.create(context, Uri.parse(preferences.getCustomFilePath(timeIndex)));
                if (mediaPlayer == null) {
                    mediaPlayer = MediaPlayer.create(context, alarm);
                    notification.tickerText = notification.tickerText + " - " + context.getString(R.string.error_playing_custom_file);
                }
            } else {
                mediaPlayer = MediaPlayer.create(context, alarm);
            }
            final short finalTimeIndex = timeIndex;
            mediaPlayer.setScreenOnWhilePlaying(true);
            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    notification.tickerText = notification.tickerText.toString().replace(" (" + Notifier.context.getString(R.string.stop) + ")", "");
                    notification.defaults = 0;
                    // New notification won't have the "(Stop)" at the end of it since we are done playing
                    startNotification(finalTimeIndex);
                }
            });
            try {
                mediaPlayer.start();
            } catch(IllegalStateException ise) {
                notification.tickerText = notification.tickerText + " - " + context.getString(R.string.error_playing_alert);
            }
            notification.defaults = Notification.DEFAULT_VIBRATE | Notification.DEFAULT_LIGHTS;
        } else {
            notification.defaults = Notification.DEFAULT_ALL;
        }
        startNotification(timeIndex);
    }

    public static void stop() {
        stopNotification();
        WakeLock.release();
    }

    private static void stopNotification() {
        if(mediaPlayer != null && mediaPlayer.isPlaying()) mediaPlayer.stop();
        if(context != null) ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }

    private static void startNotification(short timeIndex) {
        Intent i = new Intent(context, Muazzin.class);
        notification.setLatestEventInfo(context, context.getString(CONSTANT.TIME_NAMES[timeIndex]), notification.tickerText, PendingIntent.getActivity(context, 0, i, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT));
        notification.contentIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, ClickNotificationReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        notification.deleteIntent = PendingIntent.getBroadcast(context, 0, new Intent(context, ClearNotificationReceiver.class), PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(1, notification);
        if(mediaPlayer == null || !mediaPlayer.isPlaying()) {
            try {
                Thread.sleep(5000/*five seconds*/);
            } catch(Exception ex) {
                // Just trying to make sure the notification completes before we fall asleep again
            }
            WakeLock.release();
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("NewApi")
    private static void buildApi11PlusNotification(short timeIndex, long actualTime) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            notification = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setWhen(actualTime)
                    .getNotification();
        } else {
            notification = new Notification.Builder(context)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setWhen(actualTime)
                    .build();
        }
    }
}
