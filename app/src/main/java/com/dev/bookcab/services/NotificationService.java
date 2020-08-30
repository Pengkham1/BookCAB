package com.dev.bookcab.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.dev.bookcab.R;
import com.dev.bookcab.data.Customer;
import com.dev.bookcab.data.Driver;

public class NotificationService {

    private Context context;

    public enum Type {
        Canceled, Accepted, Ended
    }

    private static final String channelID = "MainNotificationChannel", channelName = "MAIN_CHANNEL";
    private static int PendingIntentRequest = 1001;


    public NotificationService(Context context) {
        this.context = context;
    }

    public void showNotification(Type type) {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(notificationManagerCompat);
        }
        notificationManagerCompat.notify(1000, createNotification(type, null, null));
    }

    public void showNotification(Type type, Driver driver) {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(notificationManagerCompat);
        }
        notificationManagerCompat.notify(1000, createNotification(type, driver, null));
    }

    public void showNotification(Type type, Customer customer) {
        NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(notificationManagerCompat);
        }
        notificationManagerCompat.notify(1000, createNotification(type, null, customer));
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createChannel(NotificationManagerCompat notificationManagerCompat) {
        NotificationChannel mainChannel = new NotificationChannel(
                channelID,
                channelName,
                NotificationManager.IMPORTANCE_HIGH
        );
        mainChannel.setDescription("This is main channel for notification");
        mainChannel.enableLights(true);
        mainChannel.setLightColor(Color.RED);
        mainChannel.enableVibration(true);
        notificationManagerCompat.createNotificationChannel(mainChannel);
    }

    private Notification createNotification(Type type, Driver driver, Customer customer) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelID);
        builder.setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle(getContentTitle(type))
                .setContentText(getMessage(type))
                .setAutoCancel(getAutoCancel(type))
                .setVibrate(createVibration())
                .setLights(Color.RED, 1, 1)
                .setSound(defaultSoundUri());
        return builder.build();
    }

    private Uri defaultSoundUri() {
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    private long[] createVibration() {
        return new long[]{0, 1000, 0};
    }

    private boolean getAutoCancel(Type type) {
        return type == Type.Ended || type == Type.Canceled;
    }

    private String getMessage(Type type) {
        if (type == Type.Canceled)
            return "Driver cancelled your request. Kindly please try again !";
        else if (type == Type.Ended)
            return "Congratulation ! Your ride has been completed. \nThank you for using our service.";
        return "Driver accepted you request ! He will be at your location soon";
    }

    private String getContentTitle(Type type) {
        if (type == Type.Accepted)
            return "Ride Request Accepted !";
        else if (type == Type.Canceled)
            return "Driver cancelled request !";
        else if (type == Type.Ended)
            return "Ride Ended !";
        return null;
    }


}
