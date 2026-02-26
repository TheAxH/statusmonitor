package com.example.statusmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.List;

public class MonitorService extends Service {

    private static final String CHANNEL_ID = "monitor_service";
    private static final int NOTIFICATION_ID = 1;
    private static final long CHECK_INTERVAL_MS = 20_000;

    private final IBinder binder = new LocalBinder();
    private Handler handler;
    private StatusChecker statusChecker;
    private NotificationHelper notificationHelper;
    private List<MonitorEntity> entities;
    private StatusUpdateListener listener;
    private boolean isRunning = false;

    public interface StatusUpdateListener {
        void onStatusUpdated(MonitorEntity entity);
    }

    public class LocalBinder extends Binder {
        MonitorService getService() {
            return MonitorService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        statusChecker = new StatusChecker(this);
        notificationHelper = new NotificationHelper(this);
        entities = MonitorConfig.getMonitors();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createForegroundNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, createForegroundNotification());
        }
        startMonitoring();
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMonitoring();
        if (statusChecker != null) {
            statusChecker.shutdown();
        }
    }

    public void setStatusUpdateListener(StatusUpdateListener listener) {
        this.listener = listener;
    }

    public List<MonitorEntity> getEntities() {
        return entities;
    }

    public void checkNow() {
        performChecks();
    }

    private void startMonitoring() {
        if (isRunning) return;
        isRunning = true;
        performChecks();
    }

    private void stopMonitoring() {
        isRunning = false;
        handler.removeCallbacksAndMessages(null);
    }

    private final Runnable checkRunnable = () -> {
        if (isRunning) {
            performChecks();
        }
    };

    private void performChecks() {
        for (MonitorEntity entity : entities) {
            statusChecker.checkStatus(entity, (checkedEntity, result) -> {
                checkedEntity.setStatus(result.status);
                checkedEntity.setMessage(result.message);
                checkedEntity.setUptime(result.uptime);
                checkedEntity.setLastCheckTime(System.currentTimeMillis());

                if (listener != null) {
                    listener.onStatusUpdated(checkedEntity);
                }

                if (checkedEntity.isNotificationsEnabled() && result.status == MonitorEntity.Status.OFFLINE) {
                    notificationHelper.notifyStatusChange(checkedEntity);
                }
            });
        }

        handler.postDelayed(checkRunnable, CHECK_INTERVAL_MS);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Status Monitor Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createForegroundNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Status Monitor")
                .setContentText("Monitoring " + entities.size() + " entities")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }
}
