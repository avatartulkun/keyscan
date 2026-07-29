package com.secureqr.scanner.clipboard;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.secureqr.scanner.MainActivity;
import com.secureqr.scanner.R;

public final class ClipboardImportNotifier {
    public static final String ACTION_IMPORT_CLIPBOARD = "com.secureqr.scanner.action.IMPORT_CLIPBOARD";
    public static final String EXTRA_FORCE_CLIPBOARD_CHECK = "force_clipboard_check";
    private static final String CHANNEL_ID = "clipboard_import";
    private static final int NOTIFICATION_ID = 4107;

    private ClipboardImportNotifier() {
    }

    public static void refresh(Context context) {
        if (context == null) return;
        if (ClipboardImportSettings.isSmartImportEnabled(context)) {
            show(context);
        } else {
            cancel(context);
        }
    }

    public static void show(Context context) {
        if (context == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        createChannel(context);
        Intent importIntent = new Intent(context, MainActivity.class)
                .setAction(ACTION_IMPORT_CLIPBOARD)
                .putExtra(EXTRA_FORCE_CLIPBOARD_CHECK, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent importPendingIntent = PendingIntent.getActivity(
                context,
                4108,
                importIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(context.getString(R.string.clipboard_import_notification_title))
                .setContentText(context.getString(R.string.clipboard_import_notification_text))
                .setContentIntent(importPendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.mipmap.ic_launcher, context.getString(R.string.clipboard_import_notification_action), importPendingIntent);
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build());
    }

    public static void cancel(Context context) {
        if (context == null) return;
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID);
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.clipboard_import_channel_name),
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription(context.getString(R.string.clipboard_import_channel_description));
        manager.createNotificationChannel(channel);
    }
}
