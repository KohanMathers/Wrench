package me.kmathers.wrench.network;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TcpServer implements Runnable {
    private final int port;
    private final Thread thread;

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    enum ConnectionState {
        HANDSHAKE,
        STATUS,
        LOGIN,
        CONFIGURATION,
        PLAY
    }

    public TcpServer(int port) {
        this.port = port;
        thread = new Thread(this);
    }

    public void start() {
        thread.start();
    }

    @SuppressWarnings("unused")
    private static void discard(Object... unused) {
        // Intentionally empty method to discard unused variables, fixes "___ is never used" warnings
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();

                new Thread(() -> {
                    try {
                        handleClient(socket);
                    } catch (IOException e) {
                        System.out.println("Client connection error: " + e.getMessage());
                    }
                }).start();
            }

        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
        }
    }

    private void handleClient(Socket socket) throws IOException {
        try (socket) {
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            ConnectionState state = ConnectionState.HANDSHAKE;
            
            OUTER:
            while (true) {
                int packetLength;
                try {
                    packetLength = readVarInt(in);
                    discard(packetLength);
                } catch (IOException e) {
                    break;
                }
                int packetId = readVarInt(in);
                switch (state) {
                    case HANDSHAKE -> {
                        if (packetId != 0x00) {
                            System.out.println("Expected handshake packet but got: " + packetId);
                            break OUTER;
                        }
                        int protocolVersion = readVarInt(in);
                        String serverAddress = readString(in);
                        int serverPort = readUnsignedShort(in);
                        discard(protocolVersion, serverAddress, serverPort);
                        
                        int nextState = readVarInt(in);
                        switch (nextState) {
                            case 1 -> state = ConnectionState.STATUS;
                            case 2 -> state = ConnectionState.LOGIN;
                            default -> {
                                System.out.println("Invalid nextState: " + nextState);
                                break OUTER;
                            }
                        }
                    }
                    case STATUS -> {
                        switch (packetId) {
                            case 0x00 ->
                                sendStatusResponse(out);
                            case 0x01 -> {
                                byte[] payload = new byte[8];
                                in.read(payload);
                                sendPongResponse(out, payload);
                                break OUTER;
                            }
                            default -> {
                                System.out.println("Unknown STATUS packet ID: " + packetId);
                                break OUTER;
                            }
                        }
                    }
                    case LOGIN -> {
                    switch (packetId) {
                        case 0x00 -> {
                            String username = readString(in);
                            sendLoginSuccess(out, username);
                            state = ConnectionState.CONFIGURATION;
                        }
                        case 0x03 -> {
                            state = ConnectionState.CONFIGURATION;
                            sendCustomPayload(out, "minecraft:brand", "Wrench".getBytes(StandardCharsets.UTF_8));
                        }
                        default -> {
                            System.out.println("Unknown LOGIN packet ID: " + packetId);
                            break OUTER;
                        }
                    }
                    }
                    case CONFIGURATION -> {
                        switch (packetId) {
                            case 0x00 -> {
                                String locale = readString(in);
                                int viewDistance = in.read();
                                int chatMode = readVarInt(in);
                                boolean chatColors = in.read() != 0;
                                int skinParts = in.read();
                                int mainHand = readVarInt(in);
                                boolean textFiltering = in.read() != 0;
                                boolean serverListings = in.read() != 0;
                                int particleStatus = readVarInt(in);
                                discard(chatMode, chatColors, skinParts, mainHand, textFiltering, serverListings, particleStatus);
                                System.out.println("Client info received: " + locale + ", view distance: " + viewDistance);
                                sendFinishConfiguration(out);
                            }
                            case 0x02 -> {
                                String channel = readString(in);
                                int remainingBytes = packetLength - 1 - getVarIntSize(packetId) - getStringSize(channel);
                                if (remainingBytes > 0) {
                                    byte[] data = new byte[remainingBytes];
                                    in.readNBytes(data, 0, remainingBytes);
                                    System.out.println("Plugin message received: " + channel + " (" + remainingBytes + " bytes)");
                                }
                            }
                            case 0x47 -> {
                                try {
                                    int remainingBytes = packetLength - 1 - getVarIntSize(packetId);
                                    byte[] allData = new byte[remainingBytes];
                                    in.readNBytes(allData, 0, remainingBytes);
                                    
                                    String dataString = new String(allData, StandardCharsets.UTF_8);
                                    if (dataString.contains("minecraft:brand")) {
                                        System.out.println("Client brand packet received");
                                        int brandIndex = dataString.indexOf("minecraft:brand");
                                        if (brandIndex >= 0 && brandIndex + 20 < dataString.length()) {
                                            String brandArea = dataString.substring(brandIndex, Math.min(brandIndex + 50, dataString.length()));
                                            System.out.println("Brand area: " + brandArea.replaceAll("[\\x00-\\x1F\\x7F-\\x9F]", "?"));
                                        }
                                    } else {
                                        System.out.println("Configuration packet 0x47 received (" + remainingBytes + " bytes)");
                                    }
                                                                        
                                } catch (Exception e) {
                                    System.out.println("Error parsing configuration packet 0x47: " + e.getMessage());
                                }
                            }
                            case 0x03 -> {
                                System.out.println("Configuration acknowledged, switching to PLAY state");
                                state = ConnectionState.PLAY;
                                break OUTER;
                            }
                            default -> {
                                System.out.println("Unknown CONFIGURATION packet ID: " + packetId);
                                debugPacket(in, packetId, packetLength, state);
                                break OUTER;
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
        }
    }

    // Packet helpers

    public static int readVarInt(InputStream input) throws IOException {
        int value = 0;
        int position = 0;
        int currentByte;

        while (true) {
            currentByte = input.read();
            if (currentByte == -1) throw new IOException("End of stream reached");

            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) break;

            position += 7;

            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }

        return value;
    }

    public static String readString(InputStream in) throws IOException {
        int length = readVarInt(in);
        byte[] bytes = in.readNBytes(length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static int readUnsignedShort(InputStream in) throws IOException {
        int byte1 = in.read();
        int byte2 = in.read();
        if (byte1 == -1 || byte2 == -1) throw new IOException("End of stream reached");
        return (byte1 << 8) | byte2;
    }

    private void debugPacket(InputStream in, int packetId, int packetLength, ConnectionState state) throws IOException {
        System.out.println("=== DEBUG PACKET ===");
        System.out.println("State: " + state);
        System.out.println("Packet ID: 0x" + String.format("%02X", packetId));
        System.out.println("Packet Length: " + packetLength);
        
        int remainingBytes = packetLength - getVarIntSize(packetId);
        if (remainingBytes > 0) {
            byte[] remainingData = new byte[remainingBytes];
            in.readNBytes(remainingData, 0, remainingBytes);
            
            System.out.println("Raw bytes (" + remainingBytes + " bytes):");
            StringBuilder hexBuilder = new StringBuilder();
            StringBuilder asciiBuilder = new StringBuilder();
            
            for (int i = 0; i < remainingData.length; i++) {
                if (i % 16 == 0 && i > 0) {
                    System.out.println(hexBuilder.toString() + " | " + asciiBuilder.toString());
                    hexBuilder.setLength(0);
                    asciiBuilder.setLength(0);
                }
                
                hexBuilder.append(String.format("%02X ", remainingData[i] & 0xFF));
                char c = (char) (remainingData[i] & 0xFF);
                asciiBuilder.append(c >= 32 && c <= 126 ? c : '.');
            }
            
            if (hexBuilder.length() > 0) {
                while (hexBuilder.length() < 48) hexBuilder.append(" ");
                System.out.println(hexBuilder.toString() + " | " + asciiBuilder.toString());
            }
        }
        System.out.println("==================");
    }

    private int getVarIntSize(int value) {
        int size = 0;
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                size++;
                return size;
            } else {
                size++;
                value >>>= 7;
            }
        }
    }

    private int getStringSize(String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        return getVarIntSize(bytes.length) + bytes.length;
    }

    // Sending packets

    private void sendStatusResponse(OutputStream out) throws IOException {
        String json = """
        {
          "version": {
            "name": "Wrench 1.21.5",
            "protocol": 770
          },
          "players": {
            "max": 20,
            "online": 1,
            "sample": [
              {
                "name": "IEatSystemFiles",
                "id": "fb6647db-ad52-4a54-9c7b-56fff703c299"
              }
            ]
          },
          "description": {
            "text": "Hello, Wrench!"
          }
        }
        """;

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        writeVarInt(buffer, 0x00);
        writeString(buffer, json);

        byte[] packetData = buffer.toByteArray();

        writeVarInt(out, packetData.length);
        out.write(packetData);
        out.flush();

    }

    private void sendPongResponse(OutputStream out, byte[] payload) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        writeVarInt(buffer, 0x01);
        buffer.write(payload);

        byte[] packetData = buffer.toByteArray();

        writeVarInt(out, packetData.length);
        out.write(packetData);
        out.flush();

    }

    private void sendLoginSuccess(OutputStream out, String username) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        writeVarInt(buffer, 0x02);

        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
        buffer.write(longToBytes(uuid.getMostSignificantBits()));
        buffer.write(longToBytes(uuid.getLeastSignificantBits()));

        writeString(buffer, username);

        writeVarInt(buffer, 0);

        byte[] packetData = buffer.toByteArray();
        writeVarInt(out, packetData.length);
        out.write(packetData);
        out.flush();
    }

    private void sendCustomPayload(OutputStream out, String channel, byte[] data) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        writeVarInt(buffer, 0x01);
        writeString(buffer, channel);
        buffer.write(data);
        
        byte[] packetData = buffer.toByteArray();
        writeVarInt(out, packetData.length);
        out.write(packetData);
        out.flush();
    }

    private void sendFinishConfiguration(OutputStream out) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        
        writeVarInt(buffer, 0x03); // Finish Configuration packet ID
        // No additional data needed
        
        byte[] packetData = buffer.toByteArray();
        writeVarInt(out, packetData.length);
        out.write(packetData);
        out.flush();
        
        System.out.println("Sent Finish Configuration packet");
    }
    // Writing helpers

    public static void writeVarInt(OutputStream out, int value) throws IOException {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                out.write(value);
                return;
            } else {
                out.write((value & SEGMENT_BITS) | CONTINUE_BIT);
                value >>>= 7;
            }
        }
    }

    public static void writeString(OutputStream out, String str) throws IOException {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }

    private static byte[] longToBytes(long value) {
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return bytes;
    }
}
