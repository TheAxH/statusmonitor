package com.example.statusmonitor;

/*
Represents a monitored entity (server, service, or program).
*/
public class MonitorEntity {

    public enum Status {
        ONLINE,
        OFFLINE,
        NO_CONNECTION  // Device has no internet / can't reach network
    }

    private final String id;
    private final String name;
    private final String address;
    private final StatusCheckStrategy checkStrategy;
    private Status status;
    private String message;
    private String uptime;  // Optional uptime string from health check
    private boolean notificationsEnabled;
    private long lastCheckTime;
    private Status previousStatus;

    private MonitorEntity(Builder builder) {
        this.id = builder.id;
        this.name = builder.name;
        this.address = builder.address;
        this.checkStrategy = builder.checkStrategy;
        this.status = Status.OFFLINE;
        this.message = "Checking...";
        this.uptime = null;
        this.notificationsEnabled = builder.notificationsEnabled;
        this.lastCheckTime = 0;
        this.previousStatus = null;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public StatusCheckStrategy getCheckStrategy() { return checkStrategy; }
    public Status getStatus() { return status; }
    public String getMessage() { return message; }
    public String getUptime() { return uptime; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public long getLastCheckTime() { return lastCheckTime; }
    public Status getPreviousStatus() { return previousStatus; }

    public void setStatus(Status status) {
        this.previousStatus = this.status;
        this.status = status;
    }

    public void setMessage(String message) { this.message = message; }
    public void setUptime(String uptime) { this.uptime = uptime; }
    public void setNotificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; }
    public void setLastCheckTime(long time) { this.lastCheckTime = time; }

    public boolean shouldNotify() {
        if (!notificationsEnabled || previousStatus == null || previousStatus == status) return false;
        return status == Status.OFFLINE || status == Status.NO_CONNECTION;
    }

    public static class Builder {
        private final String id;
        private final String name;
        private String address;
        private boolean notificationsEnabled = true;
        private StatusCheckStrategy checkStrategy;

        public Builder(String id, String name) {
            this.id = id;
            this.name = name;
        }

        public Builder address(String address) { this.address = address; return this; }
        public Builder notificationsEnabled(boolean enabled) { this.notificationsEnabled = enabled; return this; }
        public Builder checkStrategy(StatusCheckStrategy strategy) { this.checkStrategy = strategy; return this; }

        public MonitorEntity build() {
            if (checkStrategy == null) {
                throw new IllegalStateException("checkStrategy is required");
            }
            return new MonitorEntity(this);
        }
    }
}
