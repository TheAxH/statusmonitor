package com.example.statusmonitor;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StatusChecker {

    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface StatusCallback {
        void onStatusChecked(MonitorEntity entity, StatusCheckStrategy.Result result);
    }

    public StatusChecker() {
        this.executor = Executors.newCachedThreadPool();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void checkStatus(MonitorEntity entity, StatusCallback callback) {
        executor.execute(() -> {
            StatusCheckStrategy strategy = entity.getCheckStrategy();
            StatusCheckStrategy.Result result;

            try {
                result = strategy.check(entity);
            } catch (Exception e) {
                result = StatusCheckStrategy.Result.noConnection("Check failed");
            }

            final StatusCheckStrategy.Result finalResult = result;
            mainHandler.post(() -> callback.onStatusChecked(entity, finalResult));
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
