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
        LOGIN
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
                        if (packetId == 0x00) {
                            String username = readString(in);
                            sendLoginSuccess(out, username);
                            break OUTER;
                        } else {
                            System.out.println("Unknown LOGIN packet ID: " + packetId);
                            break OUTER;
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
