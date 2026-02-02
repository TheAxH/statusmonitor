package com.example.statusmonitor;

/*
Interface for custom status check implementations.
*/
public interface StatusCheckStrategy {

    class Result {
        public final MonitorEntity.Status status;
        public final String message;
        public final String uptime;  // Optional

        public Result(MonitorEntity.Status status, String message) {
            this(status, message, null);
        }

        public Result(MonitorEntity.Status status, String message, String uptime) {
            this.status = status;
            this.message = message;
            this.uptime = uptime;
        }

        public static Result online(String message) {
            return new Result(MonitorEntity.Status.ONLINE, message);
        }

        public static Result online(String message, String uptime) {
            return new Result(MonitorEntity.Status.ONLINE, message, uptime);
        }

        public static Result offline(String message) {
            return new Result(MonitorEntity.Status.OFFLINE, message);
        }

        public static Result noConnection(String message) {
            return new Result(MonitorEntity.Status.NO_CONNECTION, message);
        }
    }

    Result check(MonitorEntity entity);

    String getDescription();
}
