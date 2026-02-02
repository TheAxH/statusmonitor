package com.example.statusmonitor.checks;

import com.example.statusmonitor.MonitorEntity;
import com.example.statusmonitor.StatusCheckStrategy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/*
HTTP health check for entities running a simple health server.

Usage:
  new SimpleHealthCheck(9999, "82.117.106.223")

============================================================
SETUP ON SERVER
============================================================

apt update
apt install -y python3-fastapi python3-uvicorn python3-psutil
nano /home/health_server.py

============================================================
HEALTH SERVER CODE (/home/health_server.py)
============================================================

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
import socket
import psutil
import uvicorn
import time
from datetime import timedelta
from collections import defaultdict

app = FastAPI()

# Rate limiting: max 10 requests per second per IP
rate_limit = defaultdict(list)
MAX_REQUESTS = 10
WINDOW_SECONDS = 1

@app.middleware("http")
async def rate_limiter(request: Request, call_next):
    client_ip = request.client.host
    now = time.time()
    
    # Clean old entries
    rate_limit[client_ip] = [t for t in rate_limit[client_ip] if now - t < WINDOW_SECONDS]
    
    if len(rate_limit[client_ip]) >= MAX_REQUESTS:
        return JSONResponse(status_code=429, content={"error": "rate limited"})
    
    rate_limit[client_ip].append(now)
    return await call_next(request)

def get_uptime():
    boot_time = psutil.boot_time()
    uptime_seconds = int(time.time() - boot_time)
    return str(timedelta(seconds=uptime_seconds))

@app.get("/health")
def health():
    return {"status": "ok", "name": socket.gethostname(), "uptime": get_uptime()}

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=9999, timeout_keep_alive=5, limit_concurrency=20)

============================================================
SYSTEMD SERVICE
============================================================

nano /etc/systemd/system/health-server.service

[Unit]
Description=Health Server
After=network.target

[Service]
ExecStart=/usr/bin/python3 /home/health_server.py
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target

Then run:
systemctl daemon-reload
systemctl enable --now health-server
systemctl status health-server

============================================================
*/
public class SimpleHealthCheck implements StatusCheckStrategy {

    private static final int TIMEOUT_MS = 5000;

    private final int port;
    private final String host;

    public SimpleHealthCheck(int port, String host) {
        this.port = port;
        this.host = host;
    }

    @Override
    public Result check(MonitorEntity entity) {
        HttpURLConnection conn = null;
        try {
            String url = "http://" + host + ":" + port + "/health";
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            int code = conn.getResponseCode();
            
            if (code >= 200 && code < 300) {
                String body = readBody(conn);
                String name = extractJson(body, "name");
                String uptime = extractJson(body, "uptime");
                String message = name != null ? name : "OK";
                return Result.online(message, uptime);
            } else {
                return Result.offline("HTTP " + code);
            }

        } catch (java.net.UnknownHostException e) {
            return Result.noConnection("No DNS");
        } catch (java.net.SocketTimeoutException e) {
            return Result.offline("Timeout");
        } catch (java.net.ConnectException e) {
            return Result.offline("Connection refused");
        } catch (java.net.NoRouteToHostException e) {
            return Result.noConnection("No route");
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Network is unreachable") || msg.contains("Unable to resolve"))) {
                return Result.noConnection("No network");
            }
            return Result.offline(e.getClass().getSimpleName());
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    @Override
    public String getDescription() {
        return "HTTP:" + port;
    }

    private String readBody(HttpURLConnection conn) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractJson(String json, String key) {
        if (json == null) return null;
        String search = "\"" + key + "\"";
        int keyStart = json.indexOf(search);
        if (keyStart < 0) return null;
        int valueStart = json.indexOf("\"", keyStart + search.length() + 1);
        if (valueStart < 0) return null;
        int valueEnd = json.indexOf("\"", valueStart + 1);
        if (valueEnd < 0) return null;
        return json.substring(valueStart + 1, valueEnd);
    }
}
