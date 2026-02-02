package com.example.statusmonitor;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity implements MonitorService.StatusUpdateListener {

    private static final int NOTIFICATION_PERMISSION_CODE = 100;

    private RecyclerView recyclerView;
    private TextView subtitleText;
    private StatusAdapter adapter;
    private MonitorService monitorService;
    private boolean bound = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MonitorService.LocalBinder binder = (MonitorService.LocalBinder) service;
            monitorService = binder.getService();
            monitorService.setStatusUpdateListener(MainActivity.this);
            bound = true;

            adapter = new StatusAdapter(monitorService.getEntities());
            recyclerView.setAdapter(adapter);
            adapter.startTimerUpdates();

            int count = monitorService.getEntities().size();
            subtitleText.setText(getString(R.string.app_subtitle, count));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bound = false;
            monitorService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestNotificationPermission();

        subtitleText = findViewById(R.id.subtitleText);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Disable item change animations to prevent blinking
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }

        FloatingActionButton refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            if (bound && monitorService != null) {
                monitorService.checkNow();
            }
        });

        startMonitorService();
    }

    @Override
    protected void onStart() {
        super.onStart();
        bindToService();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unbindFromService();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (adapter != null) {
            adapter.stopTimerUpdates();
        }
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, MonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void bindToService() {
        Intent intent = new Intent(this, MonitorService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    private void unbindFromService() {
        if (bound) {
            if (monitorService != null) {
                monitorService.setStatusUpdateListener(null);
            }
            unbindService(serviceConnection);
            bound = false;
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE
                );
            }
        }
    }

    @Override
    public void onStatusUpdated(MonitorEntity entity) {
        if (adapter != null) {
            adapter.updateEntity(entity);
        }
    }
}
