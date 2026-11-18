package com.ssafy.qa;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class WirelessPortHint {
    private static final int PORT = 31483;
    private static final String PROTOCOL = "qa-adb-port-v1";

    private static Set<String> listeningPorts() throws IOException {
        Set<String> ports = new LinkedHashSet<>();
        for (String table : new String[] {"/proc/net/tcp", "/proc/net/tcp6"}) {
            try (BufferedReader reader = new BufferedReader(new FileReader(table))) {
                reader.readLine();
                for (String row; (row = reader.readLine()) != null; ) {
                    String[] fields = row.trim().split("\\s+");
                    if (fields.length < 8 || !fields[3].equals("0A") || !fields[7].equals("2000")) continue;
                    String[] address = fields[1].split(":", 2);
                    if (address.length == 2 && address[0].matches("0+")) {
                        ports.add(Integer.toString(Integer.parseInt(address[1], 16)));
                    }
                }
            }
        }
        return ports;
    }

    private static byte[] sign(Mac mac, String message) {
        byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.US_ASCII));
        byte[] hex = new byte[digest.length * 2];
        byte[] alphabet = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i < digest.length; i++) {
            hex[2 * i] = alphabet[(digest[i] & 0xff) >>> 4];
            hex[2 * i + 1] = alphabet[digest[i] & 15];
        }
        return hex;
    }

    public static void main(String[] args) throws Exception {
        String key = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.US_ASCII).trim();
        if (!key.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid port hint key");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
        try (DatagramSocket socket = new DatagramSocket(PORT)) {
            while (true) {
                byte[] buffer = new byte[160];
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);
                socket.receive(request);
                String[] fields = new String(buffer, 0, request.getLength(), StandardCharsets.US_ASCII).split(" ", -1);
                if (fields.length != 3 || !fields[0].equals(PROTOCOL) || !fields[1].matches("[0-9a-f]{32}")
                        || !MessageDigest.isEqual(sign(mac, PROTOCOL + " " + fields[1]), fields[2].getBytes(StandardCharsets.US_ASCII))) {
                    continue;
                }
                try {
                    Set<String> ports = listeningPorts();
                    if (ports.size() > 16) continue;
                    String message = PROTOCOL + " " + fields[1] + " " + String.join(",", ports);
                    String response = message + " " + new String(sign(mac, message), StandardCharsets.US_ASCII);
                    byte[] payload = response.getBytes(StandardCharsets.US_ASCII);
                    socket.send(new DatagramPacket(payload, payload.length, request.getSocketAddress()));
                } catch (IOException ignored) {
                    // Leave the request unanswered so Windows can use the fallback.
                }
            }
        }
    }
}
