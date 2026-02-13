package com.example.statusmonitor.checks;

import com.example.statusmonitor.MonitorEntity;
import com.example.statusmonitor.StatusCheckStrategy;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/*
ICMP ping check using system ping command.
Requires router/host to respond to ping.
*/
public class PingCheck implements StatusCheckStrategy {

    private final String host;

    public PingCheck(String host) {
        this.host = host;
    }

    @Override
    public Result check(MonitorEntity entity) {
        for (int attempt = 0; attempt < 3; attempt++) {
            if (attempt > 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.noConnection(e.getMessage());
                }
            }
            Result r = runPing();
            if (r != null) {
                return r;
            }
        }
        return Result.offline("No response");
    }

    private Result runPing() {
        try {
            ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", "-W", "5", host);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            String latency = null;
            String errorMsg = null;

            while ((line = reader.readLine()) != null) {
                if (line.contains("time=")) {
                    int start = line.indexOf("time=") + 5;
                    int end = line.indexOf(" ms", start);
                    if (end > start) {
                        latency = line.substring(start, end) + "ms";
                    }
                }
                if (line.contains("Network is unreachable") || line.contains("connect: Network is unreachable")) {
                    errorMsg = "No network";
                }
            }

            int exitCode = process.waitFor();

            if (exitCode == 0 && latency != null) {
                return Result.online(latency);
            }
            if (errorMsg != null) {
                return Result.noConnection(errorMsg);
            }
            return null;
        } catch (Exception e) {
            return Result.noConnection(e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "PING";
    }
}
