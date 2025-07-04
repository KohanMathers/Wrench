package org.wrench;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import org.wrench.commands.VersionCommand;
import org.wrench.listener.PlayerEventListener;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.extras.MojangAuth;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.InstanceManager;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

public class Main {
    public static void main(String[] args) {
        MinecraftServer minecraftServer = MinecraftServer.init();

        InstanceManager instanceManager = MinecraftServer.getInstanceManager();
        InstanceContainer instanceContainer = instanceManager.createInstanceContainer();

        instanceContainer.setGenerator(unit -> {
            unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK);
            });

        instanceContainer.setChunkSupplier(LightingChunk::new);

        GlobalEventHandler globalEventHandler = MinecraftServer.getGlobalEventHandler();
        globalEventHandler.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            final Player player = event.getPlayer();
            event.setSpawningInstance(instanceContainer);
            player.setRespawnPoint(new Pos(0, 42, 0));
            player.setGameMode(GameMode.CREATIVE);

            log("info", player.getUsername() + " joined the game");
        });

        PlayerEventListener.register(globalEventHandler);

        MinecraftServer.getCommandManager().register(new VersionCommand());

        MojangAuth.init();

        minecraftServer.start("0.0.0.0", 25565);
    }


    public static void log(String level, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String formatted = String.format("[%s %s] %s", time, level.toUpperCase(), message);

        switch (level.toLowerCase()) {
            case "info" -> System.out.println(formatted);
            case "warn" -> System.out.println(formatted);
            case "error" -> System.err.println(formatted);
            default -> System.out.println(formatted);
        }
    }
}