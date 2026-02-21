package com.example.statusmonitor;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusChecker {

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface StatusCallback {
        void onStatusChecked(MonitorEntity entity, StatusCheckStrategy.Result result);
    }

    public StatusChecker(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void checkStatus(MonitorEntity entity, StatusCallback callback) {
        executor.execute(() -> {
            StatusCheckStrategy strategy = entity.getCheckStrategy();
            StatusCheckStrategy.Result result;

            if (!hasActiveNetwork()) {
                result = StatusCheckStrategy.Result.noConnection("No network");
            } else {
                try {
                    result = strategy.check(entity);
                } catch (Exception e) {
                    result = StatusCheckStrategy.Result.noConnection("Check failed");
                }
            }

            final StatusCheckStrategy.Result finalResult = result;
            mainHandler.post(() -> callback.onStatusChecked(entity, finalResult));
        });
    }

    private boolean hasActiveNetwork() {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        var network = cm.getActiveNetwork();
        if (network == null) return false;
        var caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    public void shutdown() {
        executor.shutdown();
    }
}
