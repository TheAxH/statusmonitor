package com.example.statusmonitor;

import com.example.statusmonitor.checks.MinecraftCheck;
import com.example.statusmonitor.checks.PingCheck;
import com.example.statusmonitor.checks.SimpleHealthCheck;
import com.example.statusmonitor.checks.WebsiteCheck;

import java.util.ArrayList;
import java.util.List;

/*
Monitor configuration.

Available checks:
  - PingCheck(host)                  - ICMP ping
  - SimpleHealthCheck(port, host)    - HTTP GET /health
  - WebsiteCheck(url, expectedText)  - HTTP GET and check content
  - MinecraftCheck(host, port)       - Minecraft SLP protocol
*/
public class MonitorConfig {

    private static List<MonitorEntity> monitors;

    public static List<MonitorEntity> getMonitors() {
        if (monitors == null) {
            monitors = new ArrayList<>();

            String lab22PublicIP = "82.117.106.223";

            // lab22 router - ICMP ping
            monitors.add(new MonitorEntity.Builder("lab22-router", "lab22 router")
                    .address(lab22PublicIP)
                    .checkStrategy(new PingCheck(lab22PublicIP))
                    .build());

            // pve0 - HTTP health check
            monitors.add(new MonitorEntity.Builder("pve0", "pve0")
                    .address(lab22PublicIP)
                    .checkStrategy(new SimpleHealthCheck(9999, lab22PublicIP))
                    .build());

            // pve1 - HTTP health check
            monitors.add(new MonitorEntity.Builder("pve1", "pve1")
                    .address(lab22PublicIP)
                    .checkStrategy(new SimpleHealthCheck(9996, lab22PublicIP))
                    .build());

            // pve2 - HTTP health check
            monitors.add(new MonitorEntity.Builder("pve2", "pve2")
                    .address(lab22PublicIP)
                    .checkStrategy(new SimpleHealthCheck(9998, lab22PublicIP))
                    .build());

            // kaskasapakte - HTTP health check
            monitors.add(new MonitorEntity.Builder("kaskasapakte", "kaskasapakte")
                    .address(lab22PublicIP)
                    .checkStrategy(new SimpleHealthCheck(9997, lab22PublicIP))
                    .build());

            // Minecraft server
            monitors.add(new MonitorEntity.Builder("Tarfala", "Tarfala")
                    .address(lab22PublicIP)
                    .checkStrategy(new MinecraftCheck(lab22PublicIP))
                    .build());

            // xandware.se - website check
            monitors.add(new MonitorEntity.Builder("xandware", "xandware.se")
                    .address("xandware.se")
                    .checkStrategy(new WebsiteCheck("https://xandware.se", "xandware"))
                    .build());

            // mydatalog.xandware.se - website check
            monitors.add(new MonitorEntity.Builder("mydatalog", "mydatalog.xandware.se")
                    .address("mydatalog.xandware.se")
                    .checkStrategy(new WebsiteCheck("https://mydatalog.xandware.se", "mydatalog"))
                    .build());

            // rungine.se - website check
            monitors.add(new MonitorEntity.Builder("rungine", "rungine.se")
                    .address("rungine.se")
                    .checkStrategy(new WebsiteCheck("https://rungine.se", "rungine"))
                    .build());

        }
        return monitors;
    }

    public static void reset() {
        monitors = null;
    }
}
