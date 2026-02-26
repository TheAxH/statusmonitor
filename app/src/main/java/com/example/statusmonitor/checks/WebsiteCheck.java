package com.example.statusmonitor.checks;

import com.example.statusmonitor.MonitorEntity;
import com.example.statusmonitor.StatusCheckStrategy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

/*
Website check - HTTP GET and verify content contains expected text.

Usage:
  new WebsiteCheck("https://example.com", "expected text")
*/
public class WebsiteCheck implements StatusCheckStrategy {

    private static final int TIMEOUT_MS = 15000;

    private final String url;
    private final String expectedContent;

    public WebsiteCheck(String url, String expectedContent) {
        this.url = url;
        this.expectedContent = expectedContent.toLowerCase();
    }

    @Override
    public Result check(MonitorEntity entity) {
        for (int attempt = 0; attempt < 2; attempt++) {
            HttpURLConnection conn = null;
            try {
                URL urlObj = new URL(url);
                conn = (HttpURLConnection) urlObj.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT_MS);
                conn.setReadTimeout(TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "StatusMonitor/1.0");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();

                if (code >= 200 && code < 300) {
                    String body = readBody(conn);
                    if (body != null && body.toLowerCase().contains(expectedContent)) {
                        String title = extractTitle(body);
                        return Result.online(title != null ? title : "OK");
                    } else {
                        return Result.offline("Content not found");
                    }
                } else if (code >= 300 && code < 400) {
                    return Result.offline("Redirect " + code);
                } else {
                    return Result.offline("HTTP " + code);
                }

            } catch (java.net.UnknownHostException e) {
                return Result.noConnection("No DNS");
            } catch (java.net.SocketTimeoutException e) {
                if (attempt == 1) return Result.offline("Timeout");
            } catch (java.net.NoRouteToHostException e) {
                return Result.noConnection("No route");
            } catch (java.net.ConnectException e) {
                return Result.offline("Connection refused");
            } catch (javax.net.ssl.SSLException e) {
                return Result.offline("SSL error");
            } catch (Exception e) {
                return Result.offline(e.getClass().getSimpleName());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return Result.offline("Timeout");
    }

    @Override
    public String getDescription() {
        return "HTTPS";
    }

    private String readBody(HttpURLConnection conn) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int chars = 0;
            while ((line = r.readLine()) != null && chars < 50000) {
                sb.append(line);
                chars += line.length();
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private String extractTitle(String html) {
        if (html == null) return null;
        String lower = html.toLowerCase();
        int start = lower.indexOf("<title>");
        if (start < 0) return null;
        start += 7;
        int end = lower.indexOf("</title>", start);
        if (end < 0 || end <= start) return null;
        String title = html.substring(start, end).trim();
        // Clean up and limit length
        title = title.replaceAll("\\s+", " ");
        return title.length() > 30 ? title.substring(0, 27) + "..." : title;
    }
}
