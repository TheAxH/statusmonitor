package com.example.statusmonitor.checks;

import com.example.statusmonitor.MonitorEntity;
import com.example.statusmonitor.StatusCheckStrategy;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/*
Minecraft Server List Ping - checks server status using the SLP protocol.
Returns version and player count.

Usage:
  new MinecraftCheck("82.117.106.223")
  new MinecraftCheck("82.117.106.223", 25565)
*/
public class MinecraftCheck implements StatusCheckStrategy {

    private static final int TIMEOUT_MS = 5000;
    private static final int DEFAULT_PORT = 25565;

    private final String host;
    private final int port;

    public MinecraftCheck(String host) {
        this(host, DEFAULT_PORT);
    }

    public MinecraftCheck(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public Result check(MonitorEntity entity) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // Send handshake
            sendHandshake(out, host, port);
            
            // Send status request
            sendStatusRequest(out);
            
            // Read response
            String json = readStatusResponse(in);
            
            if (json == null) {
                return Result.offline("No response");
            }

            // Parse the JSON response
            String version = extractJson(json, "name");
            String onlinePlayers = extractJson(json, "online");
            String maxPlayers = extractJson(json, "max");

            StringBuilder message = new StringBuilder();
            if (version != null) {
                message.append(version);
            }
            if (onlinePlayers != null && maxPlayers != null) {
                if (message.length() > 0) message.append("\n");
                message.append(onlinePlayers).append("/").append(maxPlayers).append(" players");
            }

            return Result.online(message.length() > 0 ? message.toString() : "Online");

        } catch (java.net.UnknownHostException e) {
            return Result.noConnection("No DNS");
        } catch (java.net.SocketTimeoutException e) {
            return Result.offline("Timeout");
        } catch (java.net.NoRouteToHostException e) {
            return Result.noConnection("No route");
        } catch (java.net.ConnectException e) {
            return Result.offline("Connection refused");
        } catch (Exception e) {
            return Result.offline(e.getClass().getSimpleName());
        }
    }

    @Override
    public String getDescription() {
        return "MC:" + port;
    }

    private void sendHandshake(DataOutputStream out, String host, int port) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        DataOutputStream packet = new DataOutputStream(buffer);

        writeVarInt(packet, 0x00);           // Packet ID
        writeVarInt(packet, 765);            // Protocol version (1.20.4)
        writeString(packet, host);           // Server address
        packet.writeShort(port);             // Server port
        writeVarInt(packet, 1);              // Next state (1 = status)

        byte[] data = buffer.toByteArray();
        writeVarInt(out, data.length);
        out.write(data);
        out.flush();
    }

    private void sendStatusRequest(DataOutputStream out) throws IOException {
        writeVarInt(out, 1);     // Packet length
        writeVarInt(out, 0x00);  // Packet ID (status request)
        out.flush();
    }

    private String readStatusResponse(DataInputStream in) throws IOException {
        int packetLength = readVarInt(in);
        if (packetLength < 0) return null;

        int packetId = readVarInt(in);
        if (packetId != 0x00) return null;

        int jsonLength = readVarInt(in);
        if (jsonLength <= 0 || jsonLength > 32767) return null;

        byte[] jsonBytes = new byte[jsonLength];
        in.readFully(jsonBytes);
        return new String(jsonBytes, "UTF-8");
    }

    private void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private void writeString(DataOutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes("UTF-8");
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << position;
            if ((currentByte & 0x80) == 0) break;
            position += 7;
            if (position >= 32) throw new IOException("VarInt too big");
        }
        return value;
    }

    private String extractJson(String json, String key) {
        if (json == null) return null;
        
        // Look for "key": value or "key":"value"
        String search = "\"" + key + "\"";
        int keyStart = json.indexOf(search);
        if (keyStart < 0) return null;
        
        int colonPos = json.indexOf(":", keyStart + search.length());
        if (colonPos < 0) return null;
        
        // Skip whitespace after colon
        int valueStart = colonPos + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        
        if (valueStart >= json.length()) return null;
        
        char firstChar = json.charAt(valueStart);
        if (firstChar == '"') {
            // String value
            int valueEnd = json.indexOf("\"", valueStart + 1);
            if (valueEnd < 0) return null;
            return json.substring(valueStart + 1, valueEnd);
        } else if (Character.isDigit(firstChar) || firstChar == '-') {
            // Number value
            int valueEnd = valueStart;
            while (valueEnd < json.length() && 
                   (Character.isDigit(json.charAt(valueEnd)) || json.charAt(valueEnd) == '-' || json.charAt(valueEnd) == '.')) {
                valueEnd++;
            }
            return json.substring(valueStart, valueEnd);
        }
        
        return null;
    }
}
