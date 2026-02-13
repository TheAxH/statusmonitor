package com.example.statusmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;

public class StatusAdapter extends RecyclerView.Adapter<StatusAdapter.ViewHolder> {

    private static final String PAYLOAD_STATUS = "status";
    private static final String PAYLOAD_TIMER = "timer";
    private static final String PREFS_NAME = "status_monitor_prefs";
    private static final String PREF_NOTIFY_PREFIX = "notify_";

    private final List<MonitorEntity> entities;
    private final Handler timerHandler;
    private final Runnable timerRunnable;

    public StatusAdapter(List<MonitorEntity> entities) {
        this.entities = entities;
        this.timerHandler = new Handler(Looper.getMainLooper());
        
        this.timerRunnable = new Runnable() {
            @Override
            public void run() {
                notifyItemRangeChanged(0, entities.size(), PAYLOAD_TIMER);
                timerHandler.postDelayed(this, 1000);
            }
        };
    }

    public void startTimerUpdates() {
        timerHandler.post(timerRunnable);
    }

    public void stopTimerUpdates() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_status_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(entities.get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
            return;
        }

        MonitorEntity entity = entities.get(position);
        for (Object payload : payloads) {
            if (PAYLOAD_TIMER.equals(payload)) {
                holder.updateTimer(entity);
            } else if (PAYLOAD_STATUS.equals(payload)) {
                holder.updateStatus(entity);
            }
        }
    }

    @Override
    public int getItemCount() {
        return entities.size();
    }

    public void updateEntity(MonitorEntity entity) {
        int index = entities.indexOf(entity);
        if (index >= 0) {
            notifyItemChanged(index, PAYLOAD_STATUS);
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView card;
        private final View statusDot;
        private final TextView nameText;
        private final TextView statusBadge;
        private final TextView addressText;
        private final TextView checkTypeText;
        private final TextView messageText;
        private final TextView lastCheckText;
        private final MaterialSwitch notificationSwitch;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.card);
            statusDot = itemView.findViewById(R.id.statusDot);
            nameText = itemView.findViewById(R.id.nameText);
            statusBadge = itemView.findViewById(R.id.statusBadge);
            addressText = itemView.findViewById(R.id.addressText);
            checkTypeText = itemView.findViewById(R.id.checkTypeText);
            messageText = itemView.findViewById(R.id.messageText);
            lastCheckText = itemView.findViewById(R.id.lastCheckText);
            notificationSwitch = itemView.findViewById(R.id.notificationSwitch);
        }

        void bind(MonitorEntity entity) {
            nameText.setText(entity.getName());
            addressText.setText(entity.getAddress() != null ? entity.getAddress() : "");
            checkTypeText.setText(entity.getCheckStrategy().getDescription());

            SharedPreferences prefs = itemView.getContext().getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String key = PREF_NOTIFY_PREFIX + entity.getId();
            boolean notify = prefs.getBoolean(key, true);
            entity.setNotificationsEnabled(notify);

            notificationSwitch.setOnCheckedChangeListener(null);
            notificationSwitch.setChecked(notify);
            notificationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                entity.setNotificationsEnabled(isChecked);
                prefs.edit().putBoolean(key, isChecked).commit();
            });

            updateStatus(entity);
            updateTimer(entity);
        }

        void updateStatus(MonitorEntity entity) {
            // Build message with uptime if available
            String message = entity.getMessage();
            String uptime = entity.getUptime();
            if (uptime != null && !uptime.isEmpty()) {
                message = message + " \nuptime: " + uptime;
            }
            messageText.setText(message);

            // Status badge text
            String statusText;
            switch (entity.getStatus()) {
                case ONLINE:
                    statusText = "ONLINE";
                    break;
                case OFFLINE:
                    statusText = "OFFLINE";
                    break;
                case NO_CONNECTION:
                    statusText = "NO CONNECTION";
                    break;
                default:
                    statusText = "UNKNOWN";
            }
            statusBadge.setText(statusText);

            int statusColor = getStatusColor(entity.getStatus());
            ColorStateList colorStateList = ColorStateList.valueOf(statusColor);

            statusDot.setBackgroundTintList(colorStateList);
            statusBadge.setBackgroundTintList(colorStateList);
            card.setStrokeColor(statusColor);
        }

        void updateTimer(MonitorEntity entity) {
            if (entity.getLastCheckTime() == 0) {
                lastCheckText.setText("Checking...");
            } else {
                long elapsed = System.currentTimeMillis() - entity.getLastCheckTime();
                lastCheckText.setText(formatElapsed(elapsed));
            }
        }

        private int getStatusColor(MonitorEntity.Status status) {
            switch (status) {
                case ONLINE:
                    return ContextCompat.getColor(itemView.getContext(), R.color.status_online);
                case OFFLINE:
                    return ContextCompat.getColor(itemView.getContext(), R.color.status_offline);
                case NO_CONNECTION:
                    return ContextCompat.getColor(itemView.getContext(), R.color.status_no_connection);
                default:
                    return ContextCompat.getColor(itemView.getContext(), R.color.status_offline);
            }
        }

        private String formatElapsed(long ms) {
            long seconds = ms / 1000;
            if (seconds < 60) return seconds + "s ago";
            long minutes = seconds / 60;
            if (minutes < 60) return minutes + "m ago";
            return (minutes / 60) + "h ago";
        }
    }
}
