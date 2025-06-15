package me.kmathers.wrench.network;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.AttributeKey;

public class TcpServer {
    private final int port;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;
    private static final AttributeKey<ConnectionState> STATE_KEY = AttributeKey.valueOf("state");
    private static final AttributeKey<UUID> UUID_KEY = AttributeKey.valueOf("uuid");

    enum ConnectionState {
        HANDSHAKE,
        STATUS,
        LOGIN,
        CONFIGURATION,
        PLAY
    }

    public TcpServer(int port) {
        this.port = port;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new PacketFrameDecoder());
                            p.addLast(new PacketFrameEncoder());
                            p.addLast(new MinecraftServerHandler());
                        }
                    })
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true);

            ChannelFuture f = b.bind(port).sync();
            serverChannel = f.channel();
            System.out.println("Server is listening on port " + port);
            
            f.channel().closeFuture().sync();
        } finally {
            shutdown();
        }
    }

    public void shutdown() {
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    // Packet frame decoder - handles VarInt length prefixes
    public static class PacketFrameDecoder extends ByteToMessageDecoder {
        @Override
        protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
            in.markReaderIndex();
            
            int length;
            try {
                length = readVarInt(in);
            } catch (Exception e) {
                in.resetReaderIndex();
                return; // Not enough data
            }
            
            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return; // Not enough data
            }
            
            ByteBuf packet = in.readBytes(length);
            out.add(packet);
        }
    }

    // Packet frame encoder - adds VarInt length prefixes
    public static class PacketFrameEncoder extends MessageToByteEncoder<ByteBuf> {
        @Override
        protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) {
            writeVarInt(out, msg.readableBytes());
            out.writeBytes(msg);
        }
    }

    // Main packet handler
    public static class MinecraftServerHandler extends ChannelInboundHandlerAdapter {
        
        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            ctx.channel().attr(STATE_KEY).set(ConnectionState.HANDSHAKE);
            System.out.println("Client connected: " + ctx.channel().remoteAddress());
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            ByteBuf packet = (ByteBuf) msg;
            try {
                ConnectionState state = ctx.channel().attr(STATE_KEY).get();
                int packetId = readVarInt(packet);
                
                switch (state) {
                    case HANDSHAKE -> handleHandshake(ctx, packet, packetId);
                    case STATUS -> handleStatus(ctx, packet, packetId);
                    case LOGIN -> handleLogin(ctx, packet, packetId);
                    case CONFIGURATION -> handleConfiguration(ctx, packet, packetId);
                    case PLAY -> handlePlay(ctx, packet, packetId);
                }
            } catch (Exception e) {
                System.out.println("Error handling packet: " + e.getMessage());
                ctx.close();
            } finally {
                packet.release();
            }
        }

        private void handleHandshake(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            if (packetId != 0x00) {
                System.out.println("Expected handshake packet but got: " + packetId);
                ctx.close();
                return;
            }
            
            int protocolVersion = readVarInt(packet);
            String serverAddress = readString(packet);
            int serverPort = packet.readUnsignedShort();
            int nextState = readVarInt(packet);
            discard(protocolVersion, serverAddress, serverPort, nextState);
            
            switch (nextState) {
                case 1 -> ctx.channel().attr(STATE_KEY).set(ConnectionState.STATUS);
                case 2 -> ctx.channel().attr(STATE_KEY).set(ConnectionState.LOGIN);
                default -> {
                    System.out.println("Invalid nextState: " + nextState);
                    ctx.close();
                }
            }
        }

        private void handleStatus(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            switch (packetId) {
                case 0x00 -> sendStatusResponse(ctx);
                case 0x01 -> {
                    byte[] payload = new byte[8];
                    packet.readBytes(payload);
                    sendPongResponse(ctx, payload);
                    ctx.close();
                }
                default -> {
                    System.out.println("Unknown STATUS packet ID: " + packetId);
                    ctx.close();
                }
            }
        }

        private void handleLogin(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            switch (packetId) {
                case 0x00 -> {
                    String username = readString(packet);
                    UUID uuid = generateOfflineUUID(username);

                    System.out.println("Login start: " + username + " with UUID " + uuid);

                    ctx.channel().attr(UUID_KEY).set(uuid);

                    sendLoginSuccess(ctx, username);
                    ctx.channel().attr(STATE_KEY).set(ConnectionState.CONFIGURATION);
                }
                case 0x03 -> {
                    ctx.channel().attr(STATE_KEY).set(ConnectionState.CONFIGURATION);
                    sendCustomPayload(ctx, "minecraft:brand", "Wrench".getBytes(StandardCharsets.UTF_8));
                }
                default -> {
                    System.out.println("Unknown LOGIN packet ID: " + packetId);
                    ctx.close();
                }
            }
        }


        private void handleConfiguration(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            switch (packetId) {
                case 0x00 -> {
                    String locale = readString(packet);
                    int viewDistance = packet.readByte();
                    int chatMode = readVarInt(packet);
                    boolean chatColors = packet.readBoolean();
                    int skinParts = packet.readUnsignedByte();
                    int mainHand = readVarInt(packet);
                    boolean textFiltering = packet.readBoolean();
                    boolean serverListings = packet.readBoolean();
                    int particleStatus = readVarInt(packet);
                    discard(locale, viewDistance, chatMode, chatColors, skinParts, mainHand, textFiltering, serverListings, particleStatus);

                    sendFinishConfiguration(ctx);
                }
                case 0x02 -> {
                    String channel = readString(packet);
                    int remainingBytes = packet.readableBytes();
                    if (remainingBytes > 0) {
                        byte[] data = new byte[remainingBytes];
                        packet.readBytes(data);
                        discard(channel);
                    }
                }
                case 0x47 -> {
                    try {
                        int remainingBytes = packet.readableBytes();
                        byte[] allData = new byte[remainingBytes];
                        packet.readBytes(allData);
                        
                        String dataString = new String(allData, StandardCharsets.UTF_8);
                        if (dataString.contains("minecraft:brand")) {
                            int brandIndex = dataString.indexOf("minecraft:brand");
                            if (brandIndex >= 0 && brandIndex + 20 < dataString.length()) {
                                String brandArea = dataString.substring(brandIndex, Math.min(brandIndex + 50, dataString.length()));
                                discard(brandArea);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Error parsing configuration packet 0x47: " + e.getMessage());
                    }
                }
                case 0x03 -> {
                    System.out.println("Configuration acknowledged, switching to PLAY state");
                    ctx.channel().attr(STATE_KEY).set(ConnectionState.PLAY);
                    // Don't close here, stay connected for PLAY state
                }
                default -> {
                    System.out.println("Unknown CONFIGURATION packet ID: " + packetId);
                    debugPacket(ctx, packet, packetId);
                    ctx.close();
                }
            }
        }

        private void handlePlay(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            System.out.println("PLAY packet received: 0x" + String.format("%02X", packetId));
            // Handle play packets here, for now discard packet
            discard(packet, ctx);
        }

        private void debugPacket(ChannelHandlerContext ctx, ByteBuf packet, int packetId) {
            ConnectionState state = ctx.channel().attr(STATE_KEY).get();
            System.out.println("=== DEBUG PACKET ===");
            System.out.println("State: " + state);
            System.out.println("Packet ID: 0x" + String.format("%02X", packetId));
            System.out.println("Remaining bytes: " + packet.readableBytes());
            
            if (packet.readableBytes() > 0) {
                byte[] remainingData = new byte[packet.readableBytes()];
                packet.readBytes(remainingData);
                
                System.out.println("Raw bytes (" + remainingData.length + " bytes):");
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            System.out.println("Client connection error: " + cause.getMessage());
            ctx.close();
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            System.out.println("Client disconnected: " + ctx.channel().remoteAddress());
        }

        // Packet sending methods
        private void sendStatusResponse(ChannelHandlerContext ctx) {
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

            ByteBuf packet = Unpooled.buffer();
            writeVarInt(packet, 0x00);
            writeString(packet, json);
            ctx.writeAndFlush(packet);
        }

        private void sendPongResponse(ChannelHandlerContext ctx, byte[] payload) {
            ByteBuf packet = Unpooled.buffer();
            writeVarInt(packet, 0x01);
            packet.writeBytes(payload);
            ctx.writeAndFlush(packet);
        }

        private void sendLoginSuccess(ChannelHandlerContext ctx, String username) {
            ByteBuf packet = Unpooled.buffer();
            writeVarInt(packet, 0x02);

            UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
            packet.writeLong(uuid.getMostSignificantBits());
            packet.writeLong(uuid.getLeastSignificantBits());

            writeString(packet, username);
            writeVarInt(packet, 0);

            ctx.writeAndFlush(packet);
        }

        private void sendCustomPayload(ChannelHandlerContext ctx, String channel, byte[] data) {
            ByteBuf packet = Unpooled.buffer();
            writeVarInt(packet, 0x01);
            writeString(packet, channel);
            packet.writeBytes(data);
            ctx.writeAndFlush(packet);
        }

        private void sendFinishConfiguration(ChannelHandlerContext ctx) {
            ByteBuf packet = Unpooled.buffer();
            writeVarInt(packet, 0x03);
            ctx.writeAndFlush(packet);
            System.out.println("Sent Finish Configuration packet");
        }
    }

    // VarInt and String utilities
    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        int currentByte;

        while (true) {
            currentByte = buf.readByte();
            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) break;

            position += 7;
            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }

        return value;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                buf.writeByte(value);
                return;
            } else {
                buf.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
                value >>>= 7;
            }
        }
    }

    public static String readString(ByteBuf buf) {
        int length = readVarInt(buf);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeString(ByteBuf buf, String str) {
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    // Extra helper methods
    @SuppressWarnings("unused")
    private static void discard(Object... unused) {
        // Intentionally empty method to discard unused variables, fixes "___ is never used" warnings
    }

    public static UUID generateOfflineUUID(String username) {
    String offlinePlayerNamespace = "OfflinePlayer:" + username;
    return UUID.nameUUIDFromBytes(offlinePlayerNamespace.getBytes(StandardCharsets.UTF_8));
    }
}